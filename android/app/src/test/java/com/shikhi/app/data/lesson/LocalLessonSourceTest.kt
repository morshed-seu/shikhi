package com.shikhi.app.data.lesson

import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.content.db.ContentAnswerKeyDao
import com.shikhi.app.data.content.db.ContentReadDao
import com.shikhi.app.data.content.db.LocalExercise
import com.shikhi.app.data.content.db.LocalExerciseAnswer
import com.shikhi.app.data.content.db.LocalExerciseOption
import com.shikhi.app.data.content.db.LocalHint
import com.shikhi.app.data.content.db.LocalLesson
import com.shikhi.app.data.content.db.LocalLevel
import com.shikhi.app.data.content.db.LocalUnit
import com.shikhi.app.data.content.db.LocalVocabulary
import com.shikhi.app.data.db.LocalLessonCompletion
import com.shikhi.app.data.db.LocalLessonCompletionDao
import com.shikhi.app.data.db.LocalStatsProjection
import com.shikhi.app.data.db.LocalStatsProjectionDao
import com.shikhi.app.data.db.OutboxDao
import com.shikhi.app.data.db.OutboxEventEntity
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import com.shikhi.app.data.progress.StatsProjectionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [LocalLessonSource] end to end against fakes: bundled-content read, grading via
 * [com.shikhi.app.data.lesson.grading.LessonGradingEngine], the outbox enqueue on completion, and
 * (UO4) live hearts + ledger-gated first-completion XP via a real [StatsProjectionRepository] —
 * no network calls anywhere, no Room/Robolectric needed since the DAOs are plain Kotlin
 * interfaces here.
 */
class LocalLessonSourceTest {

	private class FakeContentReadDao : ContentReadDao {
		val lessons = mutableMapOf<String, LocalLesson>()
		val exercisesByLesson = mutableMapOf<String, List<LocalExercise>>()

		override suspend fun getVocabularyByLevel(level: String): List<LocalVocabulary> = emptyList()
		override suspend fun getLevels(): List<LocalLevel> = emptyList()
		override suspend fun getUnitsForLevel(levelId: String): List<LocalUnit> = emptyList()
		override suspend fun getLessonsForUnit(unitId: String): List<LocalLesson> = emptyList()
		override suspend fun getLesson(lessonId: String): LocalLesson? = lessons[lessonId]
		override suspend fun getExercisesForLesson(lessonId: String): List<LocalExercise> =
			exercisesByLesson[lessonId].orEmpty()

		override suspend fun insertVocabulary(rows: List<LocalVocabulary>) = Unit
		override suspend fun insertLevels(rows: List<LocalLevel>) = Unit
		override suspend fun insertUnits(rows: List<LocalUnit>) = Unit
		override suspend fun insertLessons(rows: List<LocalLesson>) = Unit
		override suspend fun insertExercises(rows: List<LocalExercise>) = Unit
		override suspend fun vocabularyCount(): Int = 0
		override suspend fun lessonCount(): Int = 0
	}

	private class FakeContentAnswerKeyDao : ContentAnswerKeyDao {
		val optionsByExercise = mutableMapOf<String, List<LocalExerciseOption>>()
		val answersByExercise = mutableMapOf<String, List<LocalExerciseAnswer>>()
		val hintsByExercise = mutableMapOf<String, List<LocalHint>>()

		override suspend fun getOptions(exerciseId: String): List<LocalExerciseOption> =
			optionsByExercise[exerciseId].orEmpty()
		override suspend fun getAnswers(exerciseId: String): List<LocalExerciseAnswer> =
			answersByExercise[exerciseId].orEmpty()
		override suspend fun getHints(exerciseId: String): List<LocalHint> =
			hintsByExercise[exerciseId].orEmpty()

		override suspend fun insertOptions(rows: List<LocalExerciseOption>) = Unit
		override suspend fun insertAnswers(rows: List<LocalExerciseAnswer>) = Unit
		override suspend fun insertHints(rows: List<LocalHint>) = Unit
	}

	private class FakeOutboxDao : OutboxDao {
		val rows = mutableListOf<OutboxEventEntity>()
		private var nextId = 1L
		override suspend fun insert(event: OutboxEventEntity): Long {
			val id = nextId++
			rows += event.copy(id = id)
			return id
		}
		override suspend fun all(): List<OutboxEventEntity> = rows.sortedBy { it.id }
		override suspend fun deleteByIds(ids: List<Long>) {
			rows.removeAll { it.id in ids }
		}
	}

	/** UO4: map-backed fake so [StatsProjectionRepository] can be constructed without Room —
	 * mirrors [LocalPracticeSourceTest]'s copy (this codebase's per-file fake convention). */
	private class FakeLocalStatsProjectionDao : LocalStatsProjectionDao {
		val rows = mutableMapOf<String, LocalStatsProjection>()
		override suspend fun get(userId: String): LocalStatsProjection? = rows[userId]
		override suspend fun upsert(row: LocalStatsProjection) { rows[row.userId] = row }
		override suspend fun rekey(oldUserId: String, newUserId: String) {
			rows.remove(oldUserId)?.let { rows[newUserId] = it.copy(userId = newUserId) }
		}
	}

	/** UO4: map-backed fake, keyed by (userId, lessonId, contentVersionId). */
	private class FakeLocalLessonCompletionDao : LocalLessonCompletionDao {
		val rows = mutableMapOf<Triple<String, String, String>, LocalLessonCompletion>()
		override suspend fun get(userId: String, lessonId: String, contentVersionId: String): LocalLessonCompletion? =
			rows[Triple(userId, lessonId, contentVersionId)]
		override suspend fun upsert(row: LocalLessonCompletion) {
			rows[Triple(row.userId, row.lessonId, row.contentVersionId)] = row
		}
		override suspend fun rekey(oldUserId: String, newUserId: String) {
			rows.keys.filter { it.first == oldUserId }.toList().forEach { key ->
				rows.remove(key)?.let { rows[Triple(newUserId, key.second, key.third)] = it.copy(userId = newUserId) }
			}
		}
		// UO6: pull-rebuild overwrite methods — not exercised by this file's tests.
		override suspend fun deleteAllForUser(userId: String) {
			rows.keys.filter { it.first == userId }.toList().forEach { rows.remove(it) }
		}
		override suspend fun upsertAll(rows: List<LocalLessonCompletion>) {
			rows.forEach { upsert(it) }
		}
	}

	/** OG1: minimal fake so tests can control what [TokenStore.localGuestId] returns — mirrors
	 * `LocalPracticeSourceTest.FakeTokenStore`. */
	private class FakeTokenStore(private var storedLocalGuestId: String? = null) : TokenStore {
		override val accessToken: StateFlow<String?> = MutableStateFlow(null)
		override suspend fun currentRefreshToken(): String? = null
		override suspend fun setSession(accessToken: String, refreshToken: String) = Unit
		override suspend fun clear() = Unit
		override suspend fun localGuestId(): String? = storedLocalGuestId
		override suspend fun setLocalGuestId(id: String) { storedLocalGuestId = id }
		override suspend fun clearLocalGuestId() { storedLocalGuestId = null }
		private var storedLastSyncedAt: Long? = null
		override suspend fun lastSyncedAt(): Long? = storedLastSyncedAt
		override suspend fun setLastSyncedAt(value: Long) { storedLastSyncedAt = value }
	}

	private lateinit var readDao: FakeContentReadDao
	private lateinit var answerKeyDao: FakeContentAnswerKeyDao
	private lateinit var outboxDao: FakeOutboxDao
	private lateinit var statsProjectionRepository: StatsProjectionRepository
	private lateinit var lessonCompletionDao: FakeLocalLessonCompletionDao
	private lateinit var source: LocalLessonSource

	private val userId = "user-1"
	private val lessonId = "lesson-1"
	private val mcqExerciseId = "ex-mcq"
	private val wordBankExerciseId = "ex-wb"

	@Before
	fun setUp() {
		readDao = FakeContentReadDao()
		answerKeyDao = FakeContentAnswerKeyDao()
		outboxDao = FakeOutboxDao()
		lessonCompletionDao = FakeLocalLessonCompletionDao()
		statsProjectionRepository = StatsProjectionRepository(FakeLocalStatsProjectionDao(), outboxDao, lessonCompletionDao)
		val workManager = mockk<androidx.work.WorkManager>(relaxed = true)
		val outbox = OutboxRepository(
			outboxDao,
			dagger.Lazy { mockk<ProgressApi>(relaxed = true) },
			dagger.Lazy { mockk<com.shikhi.app.data.api.PracticeApi>(relaxed = true) },
			dagger.Lazy { workManager },
			// UO2: this test only exercises enqueue() (via LocalLessonSource.complete()), never
			// flush()'s reconcile — relaxed mocks are never actually invoked.
			mockk(relaxed = true),
			mockk(relaxed = true),
			mockk(relaxed = true),
			mockk(relaxed = true),
		)
		val authRepository = mockk<AuthRepository>()
		every { authRepository.session } returns MutableStateFlow(SessionState.Active(User(id = userId)))
		source = LocalLessonSource(
			readDao,
			answerKeyDao,
			outbox,
			statsProjectionRepository,
			lessonCompletionDao,
			authRepository,
			FakeTokenStore(),
		)

		readDao.lessons[lessonId] = LocalLesson(
			id = lessonId, unitId = "unit-1", code = "L1", titleEn = "Greetings", titleBn = "শুভেচ্ছা", ordinal = 1,
		)
		readDao.exercisesByLesson[lessonId] = listOf(
			LocalExercise(mcqExerciseId, lessonId, "MCQ", 1, "Which is a greeting?", "কোনটি শুভেচ্ছা?", null),
			LocalExercise(wordBankExerciseId, lessonId, "WORD_BANK", 2, "Arrange: See you tomorrow", "সাজান", null),
		)
		answerKeyDao.optionsByExercise[mcqExerciseId] = listOf(
			LocalExerciseOption("opt-correct", mcqExerciseId, "Hello", "হ্যালো", isCorrect = true, ordinal = 1),
			LocalExerciseOption("opt-wrong", mcqExerciseId, "Table", "টেবিল", isCorrect = false, ordinal = 2),
		)
		answerKeyDao.optionsByExercise[wordBankExerciseId] = listOf(
			LocalExerciseOption("t1", wordBankExerciseId, "tomorrow", "tomorrow", isCorrect = false, ordinal = 1),
			LocalExerciseOption("t2", wordBankExerciseId, "See", "See", isCorrect = false, ordinal = 2),
			LocalExerciseOption("t3", wordBankExerciseId, "you", "you", isCorrect = false, ordinal = 3),
		)
		answerKeyDao.answersByExercise[wordBankExerciseId] =
			listOf(LocalExerciseAnswer("a1", wordBankExerciseId, "See you tomorrow", isPrimary = true))
	}

	@Test
	fun `start reads the bundled lesson and renders exercise config stripped of isCorrect`() = runBlocking {
		val playable = source.start(lessonId)

		assertEquals("Greetings", playable.lesson.title.en)
		assertEquals(2, playable.lesson.exercises.size)

		val mcq = playable.lesson.exercises.first { it.type == "MCQ" }
		val optionIds = mcq.config.options.orEmpty().map { it.id }.toSet()
		assertEquals(setOf("opt-correct", "opt-wrong"), optionIds)
		// The rendered options carry no isCorrect flag anywhere in the returned DTO shape —
		// ChoiceOption only has id/text, so there is nothing to accidentally leak here.

		val wordBank = playable.lesson.exercises.first { it.type == "WORD_BANK" }
		assertEquals(setOf("tomorrow", "See", "you"), wordBank.config.tokens.orEmpty().map { it.text.en }.toSet())
	}

	@Test
	fun `start reports full hearts for a fresh user`() = runBlocking {
		val playable = source.start(lessonId)
		assertEquals(5, playable.heartsRemaining)
	}

	@Test
	fun `start throws for a lesson id not present in the bundled content`() {
		assertThrows(NoSuchElementException::class.java) {
			runBlocking { source.start("no-such-lesson") }
		}
	}

	@Test
	fun `grade delegates to the local grading engine using the answer-key DAO`() = runBlocking {
		val playable = source.start(lessonId)
		val mcq = playable.lesson.exercises.first { it.type == "MCQ" }

		val correct = source.grade(playable.sessionId, mcq, buildJsonObject { put("selectedOptionId", "opt-correct") })
		assertTrue(correct.verdict.correct)

		val wrong = source.grade(playable.sessionId, mcq, buildJsonObject { put("selectedOptionId", "opt-wrong") })
		assertFalse(wrong.verdict.correct)
	}

	@Test
	fun `grade on a wrong answer decrements hearts from the full starting count`() = runBlocking {
		val playable = source.start(lessonId)
		val mcq = playable.lesson.exercises.first { it.type == "MCQ" }

		val outcome = source.grade(playable.sessionId, mcq, buildJsonObject { put("selectedOptionId", "opt-wrong") })

		assertEquals(4, outcome.heartsRemaining)
	}

	@Test
	fun `start after a prior wrong answer reports the depleted hearts count, not a fresh full count`() = runBlocking {
		val first = source.start(lessonId)
		val mcq = first.lesson.exercises.first { it.type == "MCQ" }
		source.grade(first.sessionId, mcq, buildJsonObject { put("selectedOptionId", "opt-wrong") })

		val second = source.start(lessonId)
		assertEquals(4, second.heartsRemaining)
	}

	@Test
	fun `complete enqueues a COMPLETE_LESSON outbox event with the exact lessonId, score shape`() = runBlocking {
		val playable = source.start(lessonId)

		val result = source.complete(playable.sessionId, correctCount = 2)

		assertEquals(2, result.score)
		assertEquals(1, outboxDao.rows.size)
		val event = outboxDao.rows.single()
		assertEquals(OutboxEventType.COMPLETE_LESSON, event.type)
		val payload = kotlinx.serialization.json.Json.parseToJsonElement(event.payloadJson).jsonObject
		assertEquals(lessonId, payload["lessonId"]!!.jsonPrimitive.content)
		assertEquals(2, payload["score"]!!.jsonPrimitive.content.toInt())
	}

	@Test
	fun `complete on a lesson never completed before awards correctCount times ten xp`() = runBlocking {
		val playable = source.start(lessonId)

		val result = source.complete(playable.sessionId, correctCount = 3)

		assertEquals(30, result.xpEarned)
	}

	@Test
	fun `complete called again for the same lesson awards zero xp but still enqueues a second event`() = runBlocking {
		val first = source.start(lessonId)
		val firstResult = source.complete(first.sessionId, correctCount = 3)
		assertEquals(30, firstResult.xpEarned)

		val second = source.start(lessonId)
		val secondResult = source.complete(second.sessionId, correctCount = 4)

		assertEquals("a repeat completion is ledger-gated to zero xp", 0, secondResult.xpEarned)
		assertEquals(
			"a repeat completion still enqueues its own COMPLETE_LESSON event (bestScore-style parity with the server)",
			2,
			outboxDao.rows.size,
		)
	}

	@Test
	fun `complete for an unknown session throws instead of silently enqueueing garbage`() {
		assertThrows(IllegalStateException::class.java) {
			runBlocking { source.complete("never-started", correctCount = 0) }
		}
	}
}

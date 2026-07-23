package com.shikhi.app.data.practice

import com.shikhi.app.data.api.PracticeApi
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.content.db.ContentReadDao
import com.shikhi.app.data.content.db.LocalExercise
import com.shikhi.app.data.content.db.LocalLesson
import com.shikhi.app.data.content.db.LocalLevel
import com.shikhi.app.data.content.db.LocalUnit
import com.shikhi.app.data.content.db.LocalVocabulary
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ContentCacheDao
import com.shikhi.app.data.db.LocalLessonCompletionDao
import com.shikhi.app.data.db.LocalPracticeExercise
import com.shikhi.app.data.db.LocalPracticeSession
import com.shikhi.app.data.db.LocalPracticeSessionDao
import com.shikhi.app.data.db.LocalReviewProgress
import com.shikhi.app.data.db.LocalStatsProjection
import com.shikhi.app.data.db.LocalStatsProjectionDao
import com.shikhi.app.data.db.LocalWordProgress
import com.shikhi.app.data.db.OutboxDao
import com.shikhi.app.data.db.OutboxEventEntity
import com.shikhi.app.data.db.WordProgressDao
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import com.shikhi.app.data.progress.StatsProjectionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [LocalPracticeSource] end to end against fakes: word selection, exercise generation/persistence,
 * grading + mastery/review updates via [WordProgressEngine], and the [OutboxEventType.PRACTICE_ANSWER]
 * enqueue on every graded answer — no network/Room dependency, mirroring OF3's
 * `LocalLessonSourceTest`.
 */
class LocalPracticeSourceTest {

	private class FakeContentReadDao(private val byLevel: Map<String, List<LocalVocabulary>>) : ContentReadDao {
		override suspend fun getVocabularyByLevel(level: String): List<LocalVocabulary> = byLevel[level].orEmpty()
		override suspend fun getLevels(): List<LocalLevel> = emptyList()
		override suspend fun getUnitsForLevel(levelId: String): List<LocalUnit> = emptyList()
		override suspend fun getLessonsForUnit(unitId: String): List<LocalLesson> = emptyList()
		override suspend fun getLesson(lessonId: String): LocalLesson? = null
		override suspend fun getExercisesForLesson(lessonId: String): List<LocalExercise> = emptyList()
		override suspend fun insertVocabulary(rows: List<LocalVocabulary>) = Unit
		override suspend fun insertLevels(rows: List<LocalLevel>) = Unit
		override suspend fun insertUnits(rows: List<LocalUnit>) = Unit
		override suspend fun insertLessons(rows: List<LocalLesson>) = Unit
		override suspend fun insertExercises(rows: List<LocalExercise>) = Unit
		override suspend fun vocabularyCount(): Int = byLevel.values.sumOf { it.size }
		override suspend fun lessonCount(): Int = 0
	}

	private class FakeWordProgressDao : WordProgressDao {
		val wordProgress = mutableMapOf<Pair<String, String>, LocalWordProgress>()
		val reviewProgress = mutableMapOf<Pair<String, String>, LocalReviewProgress>()
		override suspend fun getWordProgress(userId: String, vocabularyId: String) = wordProgress[userId to vocabularyId]
		override suspend fun getWordProgressFor(userId: String, vocabularyIds: List<String>) =
			vocabularyIds.mapNotNull { wordProgress[userId to it] }
		override suspend fun upsert(progress: LocalWordProgress) { wordProgress[progress.userId to progress.vocabularyId] = progress }
		override suspend fun getReviewProgress(userId: String, vocabularyId: String) = reviewProgress[userId to vocabularyId]
		override suspend fun upsertReview(progress: LocalReviewProgress) { reviewProgress[progress.userId to progress.vocabularyId] = progress }
		override suspend fun rekey(oldUserId: String, newUserId: String) {
			wordProgress.keys.filter { it.first == oldUserId }.toList().forEach { key ->
				wordProgress.remove(key)?.let { wordProgress[newUserId to key.second] = it.copy(userId = newUserId) }
			}
		}
		override suspend fun rekeyReview(oldUserId: String, newUserId: String) {
			reviewProgress.keys.filter { it.first == oldUserId }.toList().forEach { key ->
				reviewProgress.remove(key)?.let { reviewProgress[newUserId to key.second] = it.copy(userId = newUserId) }
			}
		}
		// UO6: pull-rebuild overwrite methods — not exercised by this file's tests.
		override suspend fun deleteAllForUser(userId: String) {
			wordProgress.keys.filter { it.first == userId }.toList().forEach { wordProgress.remove(it) }
		}
		override suspend fun upsertAll(rows: List<LocalWordProgress>) {
			rows.forEach { upsert(it) }
		}
		override suspend fun deleteAllReviewForUser(userId: String) {
			reviewProgress.keys.filter { it.first == userId }.toList().forEach { reviewProgress.remove(it) }
		}
		override suspend fun upsertAllReview(rows: List<LocalReviewProgress>) {
			rows.forEach { upsertReview(it) }
		}
	}

	private class FakeLocalPracticeSessionDao : LocalPracticeSessionDao {
		val sessions = mutableMapOf<String, LocalPracticeSession>()
		val exerciseRows = mutableMapOf<String, LocalPracticeExercise>()
		override suspend fun upsertSession(session: LocalPracticeSession) { sessions[session.id] = session }
		override suspend fun getSession(id: String) = sessions[id]
		override suspend fun insertExercises(exercises: List<LocalPracticeExercise>) {
			exercises.forEach { exerciseRows[it.id] = it }
		}
		override suspend fun getExercise(id: String) = exerciseRows[id]
		override suspend fun markAnswered(id: String, correct: Boolean) {
			exerciseRows[id]?.let { exerciseRows[id] = it.copy(answeredCorrect = correct) }
		}
		override suspend fun usedVocabularyIds(sessionId: String) =
			exerciseRows.values.filter { it.sessionId == sessionId }.map { it.vocabularyId }
		override suspend fun rekey(oldUserId: String, newUserId: String) {
			sessions.keys.toList().forEach { id ->
				val s = sessions.getValue(id)
				if (s.userId == oldUserId) sessions[id] = s.copy(userId = newUserId)
			}
		}
	}

	private class FakeContentCacheDao : ContentCacheDao {
		val store = mutableMapOf<String, CachedPayload>()
		override suspend fun get(key: String) = store[key]
		override suspend fun put(payload: CachedPayload) { store[payload.key] = payload }
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
		override suspend fun deleteByIds(ids: List<Long>) { rows.removeAll { it.id in ids } }
	}

	/** UO4: map-backed fake so [StatsProjectionRepository] can be constructed without Room —
	 * this test class has zero Room/Robolectric dependency, mirroring [FakeWordProgressDao]. */
	private class FakeLocalStatsProjectionDao : LocalStatsProjectionDao {
		val rows = mutableMapOf<String, LocalStatsProjection>()
		override suspend fun get(userId: String): LocalStatsProjection? = rows[userId]
		override suspend fun upsert(row: LocalStatsProjection) { rows[row.userId] = row }
		override suspend fun rekey(oldUserId: String, newUserId: String) {
			rows.remove(oldUserId)?.let { rows[newUserId] = it.copy(userId = newUserId) }
		}
	}

	/** OG1: minimal fake so tests can control what [TokenStore.localGuestId] returns. */
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

	private fun vocab(id: String, level: String = "A1") = LocalVocabulary(
		id = id, headword = "word-$id", senseLabel = null, partOfSpeech = "noun",
		cefrLevel = level, bnGloss = "gloss-$id", exampleEn = null, exampleBn = null, ordinal = 1,
	)

	private lateinit var contentDao: FakeContentReadDao
	private lateinit var wordProgressDao: FakeWordProgressDao
	private lateinit var sessionDao: FakeLocalPracticeSessionDao
	private lateinit var cacheDao: FakeContentCacheDao
	private lateinit var outboxDao: FakeOutboxDao
	private lateinit var outbox: OutboxRepository
	private lateinit var statsProjectionRepository: StatsProjectionRepository
	private lateinit var source: LocalPracticeSource

	private val userId = "user-1"

	@Before
	fun setUp() {
		val words = (1..15).map { vocab("w$it") }
		contentDao = FakeContentReadDao(mapOf("A1" to words))
		wordProgressDao = FakeWordProgressDao()
		sessionDao = FakeLocalPracticeSessionDao()
		cacheDao = FakeContentCacheDao()
		outboxDao = FakeOutboxDao()
		val workManager = mockk<androidx.work.WorkManager>(relaxed = true)
		val authRepository = mockk<AuthRepository>()
		every { authRepository.session } returns MutableStateFlow(SessionState.Active(User(id = userId)))
		outbox = OutboxRepository(
			outboxDao,
			dagger.Lazy { mockk<ProgressApi>(relaxed = true) },
			dagger.Lazy { mockk<PracticeApi>(relaxed = true) },
			dagger.Lazy { workManager },
			// UO2: this test only exercises enqueue() (PRACTICE_ANSWER emitted on grade()), never
			// flush()'s reconcile — the db/projection mocks are never actually invoked.
			mockk(relaxed = true),
			mockk(relaxed = true),
			authRepository,
			FakeTokenStore(),
		)
		// UO4: StatsProjectionRepository now also needs a LocalLessonCompletionDao — practice
		// never touches lesson completions, so a relaxed mock is sufficient here.
		statsProjectionRepository = StatsProjectionRepository(FakeLocalStatsProjectionDao(), outboxDao, mockk(relaxed = true))

		source = LocalPracticeSource(
			picker = PracticeWordPicker(contentDao, wordProgressDao),
			sessionDao = sessionDao,
			wordProgressEngine = WordProgressEngine(wordProgressDao),
			outbox = outbox,
			cacheDao = cacheDao,
			authRepository = authRepository,
			tokenStore = FakeTokenStore(),
			statsProjectionRepository = statsProjectionRepository,
		)
	}

	@Test
	fun `start defaults to A1 when there is no cached stats and persists a full round of exercises`() = runBlocking {
		val round = source.start()

		assertEquals("A1", round.cefrLevel)
		assertEquals(1, round.round)
		assertEquals(10, round.exercises.size) // ROUND_SIZE
		assertTrue(sessionDao.sessions.containsKey(round.sessionId))
		assertEquals(10, sessionDao.exerciseRows.values.count { it.sessionId == round.sessionId })
	}

	@Test
	fun `OG1 start works under a LocalGuest session, using the stored localGuestId as the userId`() = runBlocking {
		val localGuestId = "local-guest-abc"
		val authRepository = mockk<AuthRepository>()
		every { authRepository.session } returns MutableStateFlow(SessionState.LocalGuest)
		source = LocalPracticeSource(
			picker = PracticeWordPicker(contentDao, wordProgressDao),
			sessionDao = sessionDao,
			wordProgressEngine = WordProgressEngine(wordProgressDao),
			outbox = outbox,
			cacheDao = cacheDao,
			authRepository = authRepository,
			tokenStore = FakeTokenStore(localGuestId),
			statsProjectionRepository = statsProjectionRepository,
		)

		val round = source.start()

		assertEquals(localGuestId, sessionDao.sessions.getValue(round.sessionId).userId)
	}

	@Test
	fun `start uses the cached cefr level when one is present`() = runBlocking {
		val statsJson = """{"hearts":5,"xp":0,"currentStreak":0,"longestStreak":0,"rank":0,"dailyGoal":0,"cefrLevel":"A1"}"""
		cacheDao.store["stats"] = CachedPayload("stats", statsJson, 0)
		contentDao = FakeContentReadDao(mapOf("A1" to (1..15).map { vocab("w$it", "A1") }))
		source = LocalPracticeSource(
			PracticeWordPicker(contentDao, wordProgressDao), sessionDao, WordProgressEngine(wordProgressDao),
			outbox, cacheDao, mockk<AuthRepository>().also { every { it.session } returns MutableStateFlow(SessionState.Active(User(id = userId))) },
			FakeTokenStore(), statsProjectionRepository,
		)

		val round = source.start()
		assertEquals("A1", round.cefrLevel)
	}

	@Test
	fun `start throws when no vocabulary is available for the level`() {
		contentDao = FakeContentReadDao(emptyMap())
		val authRepository = mockk<AuthRepository>()
		every { authRepository.session } returns MutableStateFlow(SessionState.Active(User(id = userId)))
		source = LocalPracticeSource(
			PracticeWordPicker(contentDao, wordProgressDao), sessionDao, WordProgressEngine(wordProgressDao),
			outbox, cacheDao, authRepository, FakeTokenStore(), statsProjectionRepository,
		)

		assertThrows(IllegalStateException::class.java) { runBlocking { source.start() } }
	}

	@Test
	fun `grade updates session totals, records word progress, and enqueues a PRACTICE_ANSWER event`() = runBlocking {
		val round = source.start()
		val wordMeaning = round.exercises.first { it.type == "WORD_MEANING" }
		val correctOptionId = correctOptionIdOf(sessionDao, wordMeaning.id)

		val outcome = source.grade(round.sessionId, wordMeaning, buildJsonObject { put("selectedOptionId", correctOptionId) })

		assertTrue(outcome.verdict.correct)
		val session = sessionDao.sessions[round.sessionId]!!
		assertEquals(1, session.correctCount)
		assertEquals(1, session.totalCount)

		val vocabularyId = sessionDao.exerciseRows[wordMeaning.id]!!.vocabularyId
		assertEquals(1, wordProgressDao.wordProgress[userId to vocabularyId]!!.timesCorrect)

		assertEquals(1, outboxDao.rows.size)
		val event = outboxDao.rows.single()
		assertEquals(OutboxEventType.PRACTICE_ANSWER, event.type)
		val payload = Json.parseToJsonElement(event.payloadJson).jsonObject
		assertEquals(vocabularyId, payload["vocabularyId"]!!.jsonPrimitive.content)
		assertEquals(true, payload["correct"]!!.jsonPrimitive.content.toBoolean())
		assertTrue("answeredAt must be an ISO-8601 instant", payload["answeredAt"]!!.jsonPrimitive.content.contains("T"))
	}

	@Test
	fun `grade on a wrong answer still enqueues PRACTICE_ANSWER with correct=false`() = runBlocking {
		val round = source.start()
		val wordMeaning = round.exercises.first { it.type == "WORD_MEANING" }

		val outcome = source.grade(round.sessionId, wordMeaning, buildJsonObject { put("selectedOptionId", "not-a-real-option") })

		assertTrue(!outcome.verdict.correct)
		val payload = Json.parseToJsonElement(outboxDao.rows.single().payloadJson).jsonObject
		assertEquals(false, payload["correct"]!!.jsonPrimitive.content.toBoolean())
	}

	@Test
	fun `grading a wrong answer decrements hearts from the full starting count`() = runBlocking {
		val round = source.start()
		val wordMeaning = round.exercises.first { it.type == "WORD_MEANING" }

		val outcome = source.grade(round.sessionId, wordMeaning, buildJsonObject { put("selectedOptionId", "not-a-real-option") })

		assertEquals(4, outcome.hearts)
	}

	@Test
	fun `grading five or more wrong answers floors hearts at zero, not negative`() = runBlocking {
		val round = source.start()
		var outcome: PracticeGradeOutcome? = null
		repeat(6) {
			val exercise = round.exercises[it % round.exercises.size]
			outcome = source.grade(round.sessionId, exercise, buildJsonObject { put("selectedOptionId", "not-a-real-option") })
		}

		assertEquals(0, outcome!!.hearts)
	}

	@Test
	fun `grade throws for an unknown exercise id`() {
		val round = runBlocking { source.start() }
		assertThrows(NoSuchElementException::class.java) {
			runBlocking {
				source.grade(
					round.sessionId,
					round.exercises.first().copy(id = "no-such-exercise"),
					buildJsonObject { put("selectedOptionId", "x") },
				)
			}
		}
	}

	@Test
	fun `nextRound excludes vocabulary already used in this session`() = runBlocking {
		val round1 = source.start()
		val usedIds = sessionDao.exerciseRows.values.filter { it.sessionId == round1.sessionId }.map { it.vocabularyId }.toSet()

		val round2 = source.nextRound(round1.sessionId)

		val round2VocabIds = sessionDao.exerciseRows.values.filter { it.sessionId == round1.sessionId && it.round == 2 }.map { it.vocabularyId }
		assertTrue("round 2 must not reuse any word from round 1", round2VocabIds.none { it in usedIds })
		assertEquals(2, round2.round)
	}

	@Test
	fun `complete finalizes the session and computes xp from correctCount`() = runBlocking {
		val round = source.start()
		val wordMeaning = round.exercises.first { it.type == "WORD_MEANING" }
		val correctOptionId = correctOptionIdOf(sessionDao, wordMeaning.id)
		source.grade(round.sessionId, wordMeaning, buildJsonObject { put("selectedOptionId", correctOptionId) })

		val result = source.complete(round.sessionId)

		assertEquals(1, result.correctCount)
		assertEquals(1, result.totalCount)
		assertEquals(10, result.xpEarned) // 1 correct * XP_PER_CORRECT (10)
		assertEquals("COMPLETED", sessionDao.sessions[round.sessionId]!!.status)
	}

	private fun correctOptionIdOf(dao: FakeLocalPracticeSessionDao, exerciseId: String): String {
		val row = dao.exerciseRows.getValue(exerciseId)
		return Json.parseToJsonElement(row.answerKeyJson).jsonObject.getValue("correctOptionId").jsonPrimitive.content
	}
}

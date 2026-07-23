package com.shikhi.app.data.lesson

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.content.db.ContentDatabase
import com.shikhi.app.data.content.seed.ContentSeedImporter
import com.shikhi.app.data.db.OutboxDao
import com.shikhi.app.data.db.OutboxEventEntity
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gate OF3 end-to-end smoke test: real bundled `assets/content-seed/curriculum.json` -> real
 * [ContentSeedImporter] -> real (in-memory) [ContentDatabase] -> real [LocalLessonSource] ->
 * real [com.shikhi.app.data.lesson.grading.LessonGradingEngine]. This is the strongest evidence
 * the Kotlin grading port is behaviorally correct against real content, not just synthetic
 * fixtures — it exercises one exercise of every gradable type actually present in the real seed
 * (MCQ, TYPE_TRANSLATION, WORD_BANK, FILL_BLANK; LISTENING/MATCH aren't in the pilot content),
 * submitting both a correct and an incorrect answer for each and checking the verdict against
 * the same lesson's answer key read directly from the JSON asset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineLessonSmokeTest {

	private class FakeDataStore : DataStore<Preferences> {
		private val state = MutableStateFlow(emptyPreferences())
		override val data: Flow<Preferences> = state
		override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
			val updated = transform(state.value)
			state.value = updated
			return updated
		}
	}

	private class FakeOutboxDao : OutboxDao {
		val rows = mutableListOf<OutboxEventEntity>()
		private var nextId = 1L
		override suspend fun insert(event: OutboxEventEntity) {
			rows += event.copy(id = nextId++)
		}
		override suspend fun all(): List<OutboxEventEntity> = rows.sortedBy { it.id }
		override suspend fun deleteByIds(ids: List<Long>) {
			rows.removeAll { it.id in ids }
		}
	}

	// Real ids from assets/content-seed/curriculum.json, confirmed by direct inspection of the
	// JSON (not guessed): lesson 0001 holds the MCQ+TYPE_TRANSLATION pair used below, lesson
	// 0003 holds the WORD_BANK exercise, lesson 0021 holds the FILL_BLANK exercise.
	private val greetingsLessonId = "40000000-0000-0000-0000-000000000001"
	private val mcqExerciseId = "50000000-0000-0000-0000-000000000001"
	private val translationExerciseId = "50000000-0000-0000-0000-000000000002"
	private val wordBankLessonId = "40000000-0000-0000-0000-000000000003"
	private val wordBankExerciseId = "50000000-0000-0000-0000-000000000011"
	private val fillBlankLessonId = "40000000-0000-0000-0000-000000000021"
	private val fillBlankExerciseId = "50000000-0000-0000-0000-000000000021"

	private lateinit var database: ContentDatabase
	private lateinit var outboxDao: FakeOutboxDao
	private lateinit var source: LocalLessonSource

	@Before
	fun setUp() = runBlocking {
		val context = ApplicationProvider.getApplicationContext<Context>()
		database = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		val imported = ContentSeedImporter(context, database, FakeDataStore()).importIfNeeded()
		assertTrue("cold start: the seed hasn't imported yet, so this must run", imported)

		outboxDao = FakeOutboxDao()
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
		source = LocalLessonSource(database.contentReadDao(), database.contentAnswerKeyDao(), outbox)
	}

	@After
	fun tearDown() {
		database.close()
	}

	@Test
	fun `MCQ - correct and incorrect options against the real answer key`() = runBlocking {
		val playable = source.start(greetingsLessonId)
		val exercise = playable.lesson.exercises.first { it.id == mcqExerciseId }
		val correctOptionId = exercise.config.options!!.first { it.text.en == "Hello" }.id
		val wrongOptionId = exercise.config.options!!.first { it.text.en != "Hello" }.id

		val correct = source.grade(playable.sessionId, exercise, buildJsonObject { put("selectedOptionId", correctOptionId) })
		assertTrue(correct.verdict.correct)
		assertEquals("Correct!", correct.verdict.feedback?.en)

		val wrong = source.grade(playable.sessionId, exercise, buildJsonObject { put("selectedOptionId", wrongOptionId) })
		assertFalse(wrong.verdict.correct)
		// This exercise's only curated hint is a DEFAULT one (no WRONG_ANSWER/PATTERN in the
		// seed for exercise 001) — confirmed by direct inspection of hints.json for this id.
		assertEquals("A greeting is how you say hi to someone.", wrong.verdict.feedback?.en)
	}

	@Test
	fun `TYPE_TRANSLATION - accepted variants and curated wrong-answer hint precedence`() = runBlocking {
		val playable = source.start(greetingsLessonId)
		val exercise = playable.lesson.exercises.first { it.id == translationExerciseId }

		// Accepted answers in the real seed: "I am fine" / "I am well" / "I'm fine" — check one
		// non-primary accepted variant matches too, plus normalization (case/whitespace/punct).
		val correct = source.grade(playable.sessionId, exercise, buildJsonObject { put("text", "  I'M   FINE.  ") })
		assertTrue(correct.verdict.correct)

		// A curated WRONG_ANSWER hint exists with triggerKey "i fine" for this exact mistake.
		val wrongCurated = source.grade(playable.sessionId, exercise, buildJsonObject { put("text", "I fine") })
		assertFalse(wrongCurated.verdict.correct)
		assertTrue(wrongCurated.verdict.feedback?.en?.contains("am") == true)

		// A different wrong answer, not the curated WRONG_ANSWER key, falls through to this
		// exercise's PATTERN hint (COPULA) instead.
		val wrongOther = source.grade(playable.sessionId, exercise, buildJsonObject { put("text", "completely unrelated") })
		assertFalse(wrongOther.verdict.correct)
		assertEquals("COPULA", wrongOther.verdict.matchedPatternCode)
	}

	@Test
	fun `WORD_BANK - correct and shuffled token order against the real answer key`() = runBlocking {
		val playable = source.start(wordBankLessonId)
		val exercise = playable.lesson.exercises.first { it.id == wordBankExerciseId }
		val tokenTexts = exercise.config.tokens!!.map { it.text.en }
		assertEquals(setOf("See", "you", "tomorrow"), tokenTexts.toSet())

		val correct = source.grade(
			playable.sessionId,
			exercise,
			buildJsonObject { putJsonArray("tokenOrder") { add("See"); add("you"); add("tomorrow") } },
		)
		assertTrue(correct.verdict.correct)

		val wrong = source.grade(
			playable.sessionId,
			exercise,
			buildJsonObject { putJsonArray("tokenOrder") { add("tomorrow"); add("See"); add("you") } },
		)
		assertFalse(wrong.verdict.correct)
	}

	@Test
	fun `FILL_BLANK - correct and incorrect against the real answer key`() = runBlocking {
		val playable = source.start(fillBlankLessonId)
		val exercise = playable.lesson.exercises.first { it.id == fillBlankExerciseId }

		val correct = source.grade(playable.sessionId, exercise, buildJsonObject { put("text", "a") })
		assertTrue(correct.verdict.correct)

		val wrong = source.grade(playable.sessionId, exercise, buildJsonObject { put("text", "the") })
		assertFalse(wrong.verdict.correct)
		// The real seed curates a WRONG_ANSWER hint keyed exactly "the" for this exercise
		// (distinct from its PATTERN hint, triggerKey "ARTICLE") — confirm the WRONG_ANSWER
		// tier fired, not PATTERN: matching feedback text and no reported pattern code.
		assertEquals("Use \"a\" here — \"the\" is for something specific.", wrong.verdict.feedback?.en)
		assertEquals(null, wrong.verdict.matchedPatternCode)

		// A wrong answer that ISN'T the curated WRONG_ANSWER key falls through to PATTERN.
		val wrongOther = source.grade(playable.sessionId, exercise, buildJsonObject { put("text", "banana") })
		assertFalse(wrongOther.verdict.correct)
		assertEquals("ARTICLE", wrongOther.verdict.matchedPatternCode)
	}

	@Test
	fun `completing a fully offline lesson enqueues the exact COMPLETE_LESSON outbox shape`() = runBlocking {
		val playable = source.start(greetingsLessonId)
		val result = source.complete(playable.sessionId, correctCount = 1)

		assertEquals(1, result.score)
		val event = outboxDao.rows.single()
		assertEquals(OutboxEventType.COMPLETE_LESSON, event.type)
		val payload = kotlinx.serialization.json.Json.parseToJsonElement(event.payloadJson).jsonObject
		assertEquals(greetingsLessonId, payload["lessonId"]!!.jsonPrimitive.content)
		assertEquals(1, payload["score"]!!.jsonPrimitive.content.toInt())
	}
}

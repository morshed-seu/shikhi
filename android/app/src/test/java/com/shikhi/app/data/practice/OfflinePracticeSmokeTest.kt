package com.shikhi.app.data.practice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shikhi.app.data.api.PracticeApi
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.PracticeExercise
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.content.db.ContentDatabase
import com.shikhi.app.data.content.seed.ContentSeedImporter
import com.shikhi.app.data.db.LocalPracticeSessionDao
import com.shikhi.app.data.db.ShikhiDatabase
import com.shikhi.app.data.outbox.OutboxRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gate OF4 end-to-end smoke test (mirrors OF3's `OfflineLessonSmokeTest`): real bundled
 * `assets/content-seed/vocabulary.json` -> real [ContentSeedImporter] -> real (in-memory)
 * [ContentDatabase] + real (in-memory) [ShikhiDatabase] -> real [LocalPracticeSource], exercising
 * a full round: start -> answer correctly and incorrectly across several exercises -> confirm
 * mastery/review state actually changed -> nextRound -> complete. No network, no fakes for the
 * DAOs themselves (only [AuthRepository]/[PracticeApi]/[ProgressApi] are mocked, since nothing
 * here should ever call them).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflinePracticeSmokeTest {

	private class FakeDataStore : DataStore<Preferences> {
		private val state = MutableStateFlow(emptyPreferences())
		override val data: Flow<Preferences> = state
		override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
			val updated = transform(state.value)
			state.value = updated
			return updated
		}
	}

	private lateinit var contentDb: ContentDatabase
	private lateinit var shikhiDb: ShikhiDatabase
	private lateinit var source: LocalPracticeSource

	private val userId = "user-1"

	@Before
	fun setUp() = runBlocking {
		val context = ApplicationProvider.getApplicationContext<Context>()

		contentDb = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java).allowMainThreadQueries().build()
		val imported = ContentSeedImporter(context, contentDb, FakeDataStore()).importIfNeeded()
		assertTrue("cold start: the seed hasn't imported yet, so this must run", imported)

		shikhiDb = Room.inMemoryDatabaseBuilder(context, ShikhiDatabase::class.java).allowMainThreadQueries().build()

		val workManager = mockk<androidx.work.WorkManager>(relaxed = true)
		val authRepository = mockk<AuthRepository>()
		every { authRepository.session } returns MutableStateFlow(SessionState.Active(User(id = userId)))
		val outbox = OutboxRepository(
			shikhiDb.outboxDao(),
			dagger.Lazy { mockk<ProgressApi>(relaxed = true) },
			dagger.Lazy { mockk<PracticeApi>(relaxed = true) },
			dagger.Lazy { workManager },
			shikhiDb,
			com.shikhi.app.data.progress.StatsProjectionRepository(shikhiDb.localStatsProjectionDao(), shikhiDb.outboxDao()),
			authRepository,
			mockk(relaxed = true),
		)

		source = LocalPracticeSource(
			picker = PracticeWordPicker(contentDb.contentReadDao(), shikhiDb.wordProgressDao()),
			sessionDao = shikhiDb.localPracticeSessionDao(),
			wordProgressEngine = WordProgressEngine(shikhiDb.wordProgressDao()),
			outbox = outbox,
			cacheDao = shikhiDb.contentCacheDao(),
			authRepository = authRepository,
			tokenStore = mockk(relaxed = true),
		)
	}

	@After
	fun tearDown() {
		contentDb.close()
		shikhiDb.close()
	}

	@Test
	fun `a full offline practice round runs against the real bundled vocabulary`() = runBlocking {
		val round = source.start()
		assertEquals(10, round.exercises.size)
		assertEquals("A1", round.cefrLevel) // no cached stats yet -> defaults to A1

		var correctCount = 0
		round.exercises.forEachIndexed { i, exercise ->
			val wantCorrect = i % 2 == 0
			val answer = buildAnswer(shikhiDb.localPracticeSessionDao(), exercise, correct = wantCorrect)
			val outcome = source.grade(round.sessionId, exercise, answer)
			if (wantCorrect) {
				assertTrue("exercise ${exercise.type} expected correct", outcome.verdict.correct)
				correctCount++
			}
		}

		val touchedVocabIds = shikhiDb.localPracticeSessionDao().usedVocabularyIds(round.sessionId).toSet()
		assertEquals(10, touchedVocabIds.size)
		val progressRows = shikhiDb.wordProgressDao().getWordProgressFor(userId, touchedVocabIds.toList())
		assertEquals("every answered word must now have a local mastery row", touchedVocabIds.size, progressRows.size)
		assertTrue("at least one word answered correctly must have mastery above the unseen baseline (2)", progressRows.any { it.masteryScore > 2 })
		assertTrue("at least one word answered incorrectly must have mastery at or below the unseen baseline (2)", progressRows.any { it.masteryScore <= 2 })

		val nextRound = source.nextRound(round.sessionId)
		assertEquals(2, nextRound.round)
		val nextRoundVocabIds = shikhiDb.localPracticeSessionDao().usedVocabularyIds(round.sessionId)
			.toSet() - touchedVocabIds
		assertTrue("round 2 must not re-serve any word from round 1", nextRoundVocabIds.none { it in touchedVocabIds })

		val result = source.complete(round.sessionId)
		assertEquals(correctCount, result.correctCount)
		assertEquals(10, result.totalCount)
		assertEquals(correctCount * 10, result.xpEarned)
	}

	/** Builds a type-appropriate answer payload for [exercise], correct or deliberately wrong,
	 * by peeking at the persisted answer key (test-only — production code never does this). */
	private suspend fun buildAnswer(dao: LocalPracticeSessionDao, exercise: PracticeExercise, correct: Boolean): kotlinx.serialization.json.JsonObject {
		val row = dao.getExercise(exercise.id)!!
		val answerKey = Json.parseToJsonElement(row.answerKeyJson).jsonObject
		return when (exercise.type) {
			PracticeExerciseType.WORD_MEANING, PracticeExerciseType.MEANING_WORD, PracticeExerciseType.SENTENCE_GAP -> {
				val correctId = answerKey["correctOptionId"]!!.jsonPrimitive.content
				val optionId = if (correct) {
					correctId
				} else {
					val options = row.payloadJson.let { Json.parseToJsonElement(it).jsonObject["options"]!!.jsonArray }
					options.map { it.jsonObject["id"]!!.jsonPrimitive.content }.firstOrNull { it != correctId } ?: "no-such-option"
				}
				buildJsonObject { put("selectedOptionId", optionId) }
			}

			PracticeExerciseType.SENTENCE_BUILD -> {
				val accepted = (answerKey["accepted"] as JsonArray)[0].jsonPrimitive.content
				val tokens = if (correct) accepted.split(" ") else accepted.split(" ").reversed()
				buildJsonObject { putJsonArray("tokenOrder") { tokens.forEach { add(it) } } }
			}

			PracticeExerciseType.TYPE_WORD -> {
				val accepted = (answerKey["accepted"] as JsonArray)[0].jsonPrimitive.content
				buildJsonObject { put("text", if (correct) accepted else "definitely-not-the-word") }
			}

			else -> error("unexpected exercise type ${exercise.type}")
		}
	}
}

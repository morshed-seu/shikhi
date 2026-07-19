package com.shikhi.app.data.practice

import com.shikhi.app.data.db.LocalReviewProgress
import com.shikhi.app.data.db.LocalWordProgress
import com.shikhi.app.data.db.WordProgressDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Verbatim-behavior checks against the backend's `WordProgressServiceTest`/`WordProgressService`
 * + `FixedIntervalScheduler` (`backend/src/main/java/com/shikhi/practice/service/
 * WordProgressService.java`, `backend/.../schedule/FixedIntervalScheduler.java`): mastery
 * scoring, the graduation gate, and review-ladder promotion/demotion.
 */
class WordProgressEngineTest {

	private class FakeWordProgressDao : WordProgressDao {
		val wordProgress = mutableMapOf<Pair<String, String>, LocalWordProgress>()
		val reviewProgress = mutableMapOf<Pair<String, String>, LocalReviewProgress>()

		override suspend fun getWordProgress(userId: String, vocabularyId: String): LocalWordProgress? =
			wordProgress[userId to vocabularyId]

		override suspend fun getWordProgressFor(userId: String, vocabularyIds: List<String>): List<LocalWordProgress> =
			vocabularyIds.mapNotNull { wordProgress[userId to it] }

		override suspend fun upsert(progress: LocalWordProgress) {
			wordProgress[progress.userId to progress.vocabularyId] = progress
		}

		override suspend fun getReviewProgress(userId: String, vocabularyId: String): LocalReviewProgress? =
			reviewProgress[userId to vocabularyId]

		override suspend fun upsertReview(progress: LocalReviewProgress) {
			reviewProgress[progress.userId to progress.vocabularyId] = progress
		}
	}

	private val userId = "user-1"
	private val vocabId = "vocab-1"
	private val t0: Instant = Instant.parse("2026-01-01T00:00:00Z")

	// ---- mastery scoring -----------------------------------------------------------------

	@Test
	fun `an unseen word starts at mastery 2 and a correct answer adds 1`() = runBlocking {
		val dao = FakeWordProgressDao()
		val engine = WordProgressEngine(dao)

		engine.recordAnswer(userId, vocabId, correct = true, now = t0)

		val progress = dao.wordProgress[userId to vocabId]!!
		assertEquals(3, progress.masteryScore)
		assertEquals(1, progress.timesSeen)
		assertEquals(1, progress.timesCorrect)
		assertEquals(0, progress.timesWrong)
	}

	@Test
	fun `a wrong answer subtracts 2 and records lastWrongAt`() = runBlocking {
		val dao = FakeWordProgressDao()
		val engine = WordProgressEngine(dao)

		engine.recordAnswer(userId, vocabId, correct = false, now = t0)

		val progress = dao.wordProgress[userId to vocabId]!!
		assertEquals(0, progress.masteryScore) // 2 - 2 = 0
		assertEquals(1, progress.timesWrong)
		assertEquals(t0.toEpochMilli(), progress.lastWrongAt)
	}

	@Test
	fun `mastery clamps at the floor 0 and never goes negative`() = runBlocking {
		val dao = FakeWordProgressDao()
		val engine = WordProgressEngine(dao)

		repeat(5) { engine.recordAnswer(userId, vocabId, correct = false, now = t0) }

		assertEquals(0, dao.wordProgress[userId to vocabId]!!.masteryScore)
	}

	@Test
	fun `mastery clamps at the ceiling 5 and never exceeds it`() = runBlocking {
		val dao = FakeWordProgressDao()
		val engine = WordProgressEngine(dao)

		repeat(10) { engine.recordAnswer(userId, vocabId, correct = true, now = t0) }

		assertEquals(5, dao.wordProgress[userId to vocabId]!!.masteryScore)
	}

	// ---- graduation gate -------------------------------------------------------------------

	@Test
	fun `graduation requires all three thresholds and happens on the qualifying correct answer`() = runBlocking {
		val dao = FakeWordProgressDao()
		val engine = WordProgressEngine(dao)

		engine.recordAnswer(userId, vocabId, correct = true, now = t0) // mastery 3, seen 1, correct 1
		assertNull("not enough timesSeen/timesCorrect yet", dao.reviewProgress[userId to vocabId])

		engine.recordAnswer(userId, vocabId, correct = true, now = t0) // mastery 4, seen 2, correct 2
		assertNull("timesSeen still short of 3", dao.reviewProgress[userId to vocabId])

		engine.recordAnswer(userId, vocabId, correct = true, now = t0) // mastery 5, seen 3, correct 3
		val review = dao.reviewProgress[userId to vocabId]
		assertTrue("all three thresholds met on a correct answer must graduate the word", review != null)
		assertEquals(1, review!!.reviewStage)
		assertEquals(t0.plus(Duration.ofDays(1)).toEpochMilli(), review.dueAt)
	}

	@Test
	fun `a wrong answer never graduates a word even if the resulting counters would satisfy every threshold`() = runBlocking {
		val dao = FakeWordProgressDao()
		// Pre-seed state one wrong answer away from numerically satisfying every graduation
		// threshold (mastery 5-2=3, timesCorrect unchanged at 2, timesSeen 2+1=3).
		dao.wordProgress[userId to vocabId] = LocalWordProgress(
			userId = userId, vocabularyId = vocabId,
			timesSeen = 2, timesCorrect = 2, timesWrong = 0, masteryScore = 5, lastSeenAt = 0,
		)
		val engine = WordProgressEngine(dao)

		engine.recordAnswer(userId, vocabId, correct = false, now = t0)

		val progress = dao.wordProgress[userId to vocabId]!!
		assertEquals(3, progress.masteryScore)
		assertEquals(2, progress.timesCorrect)
		assertEquals(3, progress.timesSeen)
		assertNull("graduation must never happen on a wrong answer, even a threshold-satisfying one", dao.reviewProgress[userId to vocabId])
	}

	// ---- review ladder -----------------------------------------------------------------------

	@Test
	fun `a correct answer on a NON-due word leaves the ladder untouched`() = runBlocking {
		val dao = FakeWordProgressDao()
		val notDueYet = t0.plus(Duration.ofDays(10))
		dao.reviewProgress[userId to vocabId] = LocalReviewProgress(
			userId = userId, vocabularyId = vocabId, reviewStage = 1, dueAt = notDueYet.toEpochMilli(),
		)
		val engine = WordProgressEngine(dao)

		engine.recordAnswer(userId, vocabId, correct = true, now = t0) // now is well before dueAt

		val review = dao.reviewProgress[userId to vocabId]!!
		assertEquals(1, review.reviewStage)
		assertEquals(notDueYet.toEpochMilli(), review.dueAt)
	}

	@Test
	fun `a correct answer on a DUE word promotes one stage and pushes dueAt out`() = runBlocking {
		val dao = FakeWordProgressDao()
		dao.reviewProgress[userId to vocabId] = LocalReviewProgress(
			userId = userId, vocabularyId = vocabId, reviewStage = 1, dueAt = t0.toEpochMilli(),
		)
		val engine = WordProgressEngine(dao)

		engine.recordAnswer(userId, vocabId, correct = true, now = t0)

		val review = dao.reviewProgress[userId to vocabId]!!
		assertEquals(2, review.reviewStage) // 1 -> 2
		assertEquals(t0.plus(Duration.ofDays(3)).toEpochMilli(), review.dueAt) // ladder[2] = 3 days
		assertEquals(1, review.reviewCount)
		assertEquals(1, review.successfulReviews)
	}

	@Test
	fun `a late review (answered well past dueAt) still promotes normally`() = runBlocking {
		val dao = FakeWordProgressDao()
		dao.reviewProgress[userId to vocabId] = LocalReviewProgress(
			userId = userId, vocabularyId = vocabId, reviewStage = 1, dueAt = t0.toEpochMilli(),
		)
		val engine = WordProgressEngine(dao)
		val muchLater = t0.plus(Duration.ofDays(30))

		engine.recordAnswer(userId, vocabId, correct = true, now = muchLater)

		assertEquals(2, dao.reviewProgress[userId to vocabId]!!.reviewStage)
	}

	@Test
	fun `a wrong answer always demotes two stages floored at 0, regardless of due status`() = runBlocking {
		val dao = FakeWordProgressDao()
		val notDueYet = t0.plus(Duration.ofDays(10))
		dao.reviewProgress[userId to vocabId] = LocalReviewProgress(
			userId = userId, vocabularyId = vocabId, reviewStage = 3, dueAt = notDueYet.toEpochMilli(),
		)
		val engine = WordProgressEngine(dao)

		engine.recordAnswer(userId, vocabId, correct = false, now = t0) // now is before dueAt, but demotion is unconditional

		val review = dao.reviewProgress[userId to vocabId]!!
		assertEquals(1, review.reviewStage) // 3 - 2 = 1
		assertEquals(t0.plus(Duration.ofDays(1)).toEpochMilli(), review.dueAt) // ladder[1] = 1 day
		assertEquals(1, review.failedReviews)
		assertEquals(1, review.failureStreak)
	}

	@Test
	fun `demotion floors at stage 0 and never goes negative`() = runBlocking {
		val dao = FakeWordProgressDao()
		dao.reviewProgress[userId to vocabId] = LocalReviewProgress(
			userId = userId, vocabularyId = vocabId, reviewStage = 0, dueAt = t0.toEpochMilli(),
		)
		val engine = WordProgressEngine(dao)

		engine.recordAnswer(userId, vocabId, correct = false, now = t0)

		val review = dao.reviewProgress[userId to vocabId]!!
		assertEquals(0, review.reviewStage)
		assertEquals(t0.toEpochMilli(), review.dueAt) // ladder[0] = 0 days
	}

	// ---- fixed-interval day ladder -------------------------------------------------------------

	@Test
	fun `the day ladder matches the backend's default stages exactly`() {
		val expectedDays = listOf(0, 1, 3, 7, 14, 30, 60, 120, 180, 365)
		expectedDays.forEachIndexed { stage, days ->
			assertEquals(Duration.ofDays(days.toLong()), FixedIntervalScheduler.interval(stage))
		}
	}

	@Test
	fun `an out-of-range stage clamps to the nearest ladder end`() {
		assertEquals(Duration.ofDays(0), FixedIntervalScheduler.interval(-5))
		assertEquals(Duration.ofDays(365), FixedIntervalScheduler.interval(999))
		assertEquals(9, FixedIntervalScheduler.maxStage())
	}
}

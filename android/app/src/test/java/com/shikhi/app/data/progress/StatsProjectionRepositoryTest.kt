package com.shikhi.app.data.progress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.db.OutboxEventEntity
import com.shikhi.app.data.db.ShikhiDatabase
import com.shikhi.app.data.outbox.OutboxEventType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UO2 (docs/95-unified-offline-online-design.md §3.1, `~/.claude/plans/unified-offline-online/
 * 00-shared-context.md`'s reconciliation model): pins the core invariant this whole gate exists
 * for — XP is derived (`baselineXp + Σ pendingXpDelta`), never a stored counter, and collapsing a
 * batch of outbox rows into a new baseline must never double-count what they already contributed
 * to `displayXp` while pending.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsProjectionRepositoryTest {

	private lateinit var db: ShikhiDatabase
	private lateinit var repository: StatsProjectionRepository

	private val userId = "user-1"

	@Before
	fun setUp() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		db = Room.inMemoryDatabaseBuilder(context, ShikhiDatabase::class.java).allowMainThreadQueries().build()
		repository = StatsProjectionRepository(db.localStatsProjectionDao(), db.outboxDao())
	}

	@After
	fun tearDown() {
		db.close()
	}

	private suspend fun enqueuePracticeAnswer(correct: Boolean) {
		db.outboxDao().insert(
			OutboxEventEntity(
				idempotencyKey = "idem-${System.nanoTime()}",
				type = OutboxEventType.PRACTICE_ANSWER,
				payloadJson = buildJsonObject {
					put("vocabularyId", "vocab-1")
					put("correct", correct)
					put("answeredAt", "2026-07-23T00:00:00Z")
				}.toString(),
				createdAt = 0L,
			),
		)
	}

	private suspend fun enqueueCompleteLesson(score: Int) {
		db.outboxDao().insert(
			OutboxEventEntity(
				idempotencyKey = "idem-${System.nanoTime()}",
				type = OutboxEventType.COMPLETE_LESSON,
				payloadJson = buildJsonObject {
					put("lessonId", "lesson-1")
					put("score", score)
				}.toString(),
				createdAt = 0L,
			),
		)
	}

	@Test
	fun `ensureRow is idempotent and seeds zeroed defaults`() = runBlocking {
		repository.ensureRow(userId)
		repository.ensureRow(userId) // second call must be a no-op, not overwrite

		val row = db.localStatsProjectionDao().get(userId)
		assertNotNull(row)
		assertEquals(0, row?.baselineXp)
		assertEquals("A1", row?.cefrLevel)
	}

	@Test
	fun `displayXp with no row at all defaults baseline to zero rather than crashing`() = runBlocking {
		enqueuePracticeAnswer(correct = true)
		assertEquals(10, repository.displayXp(userId))
	}

	@Test
	fun `displayXp sums baseline plus every pending delta`() = runBlocking {
		repository.reconcile(userId, Stats(xp = 100))
		enqueuePracticeAnswer(correct = true)
		enqueuePracticeAnswer(correct = false) // wrong answer contributes 0
		enqueueCompleteLesson(score = 4) // 4 * 10

		assertEquals(100 + 10 + 0 + 40, repository.displayXp(userId))
	}

	@Test
	fun `ANSWER, SET_LEVEL and RETRY_PRACTICE_SUBMIT events contribute zero`() = runBlocking {
		repository.reconcile(userId, Stats(xp = 50))
		db.outboxDao().insert(
			OutboxEventEntity(
				idempotencyKey = "idem-answer",
				type = OutboxEventType.ANSWER,
				payloadJson = "{}",
				createdAt = 0L,
			),
		)
		db.outboxDao().insert(
			OutboxEventEntity(
				idempotencyKey = "idem-set-level",
				type = "SET_LEVEL",
				payloadJson = "{}",
				createdAt = 0L,
			),
		)
		db.outboxDao().insert(
			OutboxEventEntity(
				idempotencyKey = "idem-retry",
				type = OutboxEventType.RETRY_PRACTICE_SUBMIT,
				payloadJson = "{}",
				createdAt = 0L,
			),
		)

		assertEquals(50, repository.displayXp(userId))
	}

	@Test
	fun `reconcile collapses pending deltas into the baseline with no double-count`() = runBlocking {
		repository.reconcile(userId, Stats(xp = 0))
		enqueuePracticeAnswer(correct = true)
		enqueuePracticeAnswer(correct = true)
		enqueuePracticeAnswer(correct = true)

		assertEquals("3 correct answers pending on top of a zero baseline", 30, repository.displayXp(userId))

		// Simulate exactly what OutboxRepository.flush() does inside its transaction: the server's
		// Stats already accounts for these 3 events, and the rows that contributed them are
		// deleted in the same breath.
		val pendingIds = db.outboxDao().all().map { it.id }
		db.outboxDao().deleteByIds(pendingIds)
		repository.reconcile(userId, Stats(xp = 30))

		assertEquals(
			"collapsing pending into baseline must not double it — 30, not 60",
			30,
			repository.displayXp(userId),
		)
	}

	@Test
	fun `a delayed replayed reconcile with the same idempotent Stats converges to the same display value`() = runBlocking {
		repository.reconcile(userId, Stats(xp = 75, hearts = 4, currentStreak = 2))
		val first = repository.displayXp(userId)

		// A retried/duplicate sync response carrying the exact same server-idempotent Stats
		// (e.g. a replayed network response) must be safe to apply again.
		repository.reconcile(userId, Stats(xp = 75, hearts = 4, currentStreak = 2))
		val second = repository.displayXp(userId)

		assertEquals(first, second)
		assertEquals(75, second)
	}

	@Test
	fun `reconcile overwrites non-additive fields wholesale from server truth`() = runBlocking {
		repository.reconcile(userId, Stats(xp = 10, hearts = 5, currentStreak = 1, longestStreak = 1, rank = 100, dailyGoal = 10, cefrLevel = "A1"))
		repository.reconcile(userId, Stats(xp = 20, hearts = 2, currentStreak = 6, longestStreak = 6, rank = 50, dailyGoal = 20, cefrLevel = "B1"))

		val row = db.localStatsProjectionDao().get(userId)
		assertEquals(20, row?.baselineXp)
		assertEquals(2, row?.hearts)
		assertEquals(6, row?.currentStreak)
		assertEquals(6, row?.longestStreak)
		assertEquals(50, row?.rank)
		assertEquals(20, row?.dailyGoal)
		assertEquals("B1", row?.cefrLevel)
	}
}

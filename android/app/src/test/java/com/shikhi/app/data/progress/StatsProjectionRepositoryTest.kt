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
import java.time.LocalDate

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
		repository = StatsProjectionRepository(db.localStatsProjectionDao(), db.outboxDao(), db.localLessonCompletionDao())
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

	/**
	 * UO4: `COMPLETE_LESSON` events only contribute XP when a matching [LocalLessonCompletion]
	 * ledger row pins THIS event as the lesson's first completion (see
	 * [StatsProjectionRepository]'s `pendingXpDelta` doc) — this helper enqueues the event AND
	 * writes that ledger row, mirroring what [com.shikhi.app.data.lesson.LocalLessonSource.complete]
	 * does for a genuine first-time completion, which is what every existing (pre-UO4) call site of
	 * this helper actually intends to simulate.
	 */
	private suspend fun enqueueCompleteLesson(score: Int, lessonId: String = "lesson-1") {
		val eventId = db.outboxDao().insert(
			OutboxEventEntity(
				idempotencyKey = "idem-${System.nanoTime()}",
				type = OutboxEventType.COMPLETE_LESSON,
				payloadJson = buildJsonObject {
					put("lessonId", lessonId)
					put("score", score)
				}.toString(),
				createdAt = 0L,
			),
		)
		db.localLessonCompletionDao().upsert(
			com.shikhi.app.data.db.LocalLessonCompletion(
				userId = userId,
				lessonId = lessonId,
				contentVersionId = "",
				firstCompletionEventId = eventId,
				completedAt = 0L,
			),
		)
	}

	@Test
	fun `ensureRow is idempotent and seeds zeroed defaults with full hearts`() = runBlocking {
		repository.ensureRow(userId)
		repository.ensureRow(userId) // second call must be a no-op, not overwrite

		val row = db.localStatsProjectionDao().get(userId)
		assertNotNull(row)
		assertEquals(0, row?.baselineXp)
		assertEquals("A1", row?.cefrLevel)
		// UO4 fix: a fresh account's true starting hearts is MAX_HEARTS (5), not Stats()'s wire
		// default of 0.
		assertEquals(5, row?.hearts)
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

	// ---- UO4: registerActiveDay / loseHeart / currentHearts / hasReconciled / overlay ------

	@Test
	fun `registerActiveDay on a fresh row sets streak to 1, refills hearts, and sets lastActiveDate`() = runBlocking {
		val today = LocalDate.parse("2026-07-23")
		repository.registerActiveDay(userId, today)

		val row = db.localStatsProjectionDao().get(userId)
		assertEquals(1, row?.currentStreak)
		assertEquals(1, row?.longestStreak)
		assertEquals(5, row?.hearts)
		assertEquals("2026-07-23", row?.lastActiveDate)
	}

	@Test
	fun `registerActiveDay called twice on the same day is a no-op the second time`() = runBlocking {
		val today = LocalDate.parse("2026-07-23")
		repository.registerActiveDay(userId, today)
		repository.loseHeart(userId) // so a second registerActiveDay refilling would be observable
		repository.registerActiveDay(userId, today)

		val row = db.localStatsProjectionDao().get(userId)
		assertEquals("second call on the same day must not double-increment the streak", 1, row?.currentStreak)
		assertEquals("second call on the same day must be a no-op, not a hearts refill", 4, row?.hearts)
	}

	@Test
	fun `registerActiveDay on the day after lastActiveDate increments the streak`() = runBlocking {
		repository.registerActiveDay(userId, LocalDate.parse("2026-07-22"))
		repository.registerActiveDay(userId, LocalDate.parse("2026-07-23"))

		val row = db.localStatsProjectionDao().get(userId)
		assertEquals(2, row?.currentStreak)
		assertEquals(2, row?.longestStreak)
	}

	@Test
	fun `registerActiveDay after a gap of more than one day resets the streak to 1`() = runBlocking {
		repository.registerActiveDay(userId, LocalDate.parse("2026-07-20"))
		repository.registerActiveDay(userId, LocalDate.parse("2026-07-23")) // 3-day gap

		val row = db.localStatsProjectionDao().get(userId)
		assertEquals(1, row?.currentStreak)
		assertEquals("longest streak from before the gap must survive", 1, row?.longestStreak)
	}

	@Test
	fun `loseHeart decrements and floors at zero rather than going negative`() = runBlocking {
		repository.ensureRow(userId)
		repeat(7) { repository.loseHeart(userId) } // more than MAX_HEARTS (5) wrong answers

		val row = db.localStatsProjectionDao().get(userId)
		assertEquals(0, row?.hearts)
	}

	@Test
	fun `currentHearts seeds a row if absent and returns full hearts`() = runBlocking {
		assertEquals(5, repository.currentHearts(userId))
		assertNotNull(db.localStatsProjectionDao().get(userId))
	}

	@Test
	fun `hasReconciled is false before any reconcile call and true after`() = runBlocking {
		repository.ensureRow(userId)
		assertEquals(false, repository.hasReconciled(userId))

		repository.reconcile(userId, Stats(xp = 10))
		assertEquals(true, repository.hasReconciled(userId))
	}

	@Test
	fun `overlay returns the input stats unchanged when no projection row exists`() = runBlocking {
		val input = Stats(xp = 99, hearts = 2, currentStreak = 3, longestStreak = 4, cefrLevel = "B2")
		val overlaid = repository.overlay(userId, input)
		assertEquals(input, overlaid)
	}

	@Test
	fun `overlay applies xp, hearts, streak, and cefrLevel from the local projection`() = runBlocking {
		repository.reconcile(userId, Stats(xp = 50, hearts = 5, currentStreak = 1, longestStreak = 1, cefrLevel = "A1"))
		repository.registerActiveDay(userId, LocalDate.parse("2026-07-23"))
		enqueuePracticeAnswer(correct = true) // +10 pending

		val stale = Stats(xp = 0, hearts = 0, currentStreak = 0, longestStreak = 0, cefrLevel = "Z9")
		val overlaid = repository.overlay(userId, stale)

		assertEquals("overlay must use displayXp, not the raw baseline", 60, overlaid.xp)
		assertEquals(5, overlaid.hearts)
		assertEquals(1, overlaid.currentStreak)
		assertEquals("A1", overlaid.cefrLevel)
	}

	// ---- UO4: COMPLETE_LESSON ledger-gated pendingXpDelta -----------------------------------

	@Test
	fun `displayXp counts a COMPLETE_LESSON event whose id matches the ledger's firstCompletionEventId`() = runBlocking {
		val eventId = db.outboxDao().insert(
			OutboxEventEntity(
				idempotencyKey = "idem-lesson-1",
				type = OutboxEventType.COMPLETE_LESSON,
				payloadJson = buildJsonObject { put("lessonId", "lesson-1"); put("score", 3) }.toString(),
				createdAt = 0L,
			),
		)
		db.localLessonCompletionDao().upsert(
			com.shikhi.app.data.db.LocalLessonCompletion(
				userId = userId,
				lessonId = "lesson-1",
				contentVersionId = "",
				firstCompletionEventId = eventId,
				completedAt = 0L,
			),
		)

		assertEquals(30, repository.displayXp(userId))
	}

	@Test
	fun `displayXp does not count a COMPLETE_LESSON event that is not the ledger's first-completion event`() = runBlocking {
		val eventId = db.outboxDao().insert(
			OutboxEventEntity(
				idempotencyKey = "idem-lesson-repeat",
				type = OutboxEventType.COMPLETE_LESSON,
				payloadJson = buildJsonObject { put("lessonId", "lesson-1"); put("score", 3) }.toString(),
				createdAt = 0L,
			),
		)
		// Ledger says some EARLIER event (not this one) was the real first completion.
		db.localLessonCompletionDao().upsert(
			com.shikhi.app.data.db.LocalLessonCompletion(
				userId = userId,
				lessonId = "lesson-1",
				contentVersionId = "",
				firstCompletionEventId = eventId - 1,
				completedAt = 0L,
			),
		)

		assertEquals(0, repository.displayXp(userId))
	}
}

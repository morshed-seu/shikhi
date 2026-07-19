package com.shikhi.app.data.practice

import com.shikhi.app.data.db.LocalReviewProgress
import com.shikhi.app.data.db.LocalWordProgress
import com.shikhi.app.data.db.WordProgressDao
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Fixed day-count ladder, a verbatim port of the backend's `FixedIntervalScheduler`
 * (`backend/src/main/java/com/shikhi/practice/schedule/FixedIntervalScheduler.java`):
 * `[0, 1, 3, 7, 14, 30, 60, 120, 180, 365]` days. Stage 0 means "due immediately" (freshly
 * demoted words); a stage outside the ladder clamps to the nearest end. The backend makes this
 * configurable (`shikhi.practice.planner.review-intervals-days`); nothing in this app reads that
 * property, so the default list is hardcoded here, matching how `PlannerProperties`' graduation
 * thresholds are hardcoded below rather than plumbed through as config (design doc §4.3: "hardcode
 * the 3 graduation-threshold defaults as Kotlin constants").
 */
object FixedIntervalScheduler {
	private val LADDER_DAYS = listOf(0, 1, 3, 7, 14, 30, 60, 120, 180, 365)

	fun interval(stage: Int): Duration {
		val clamped = stage.coerceIn(0, maxStage())
		return Duration.ofDays(LADDER_DAYS[clamped].toLong())
	}

	fun maxStage(): Int = LADDER_DAYS.size - 1
}

private fun LocalReviewProgress.isDue(nowMillis: Long): Boolean = dueAt <= nowMillis

/** Correct answer while due: advance one stage and push the next due date out. */
private fun LocalReviewProgress.promote(now: Instant, nextInterval: Duration): LocalReviewProgress = copy(
	reviewStage = reviewStage + 1,
	dueAt = now.plus(nextInterval).toEpochMilli(),
	lastReviewedAt = now.toEpochMilli(),
	reviewCount = reviewCount + 1,
	successfulReviews = successfulReviews + 1,
	failureStreak = 0,
)

/** Wrong answer: drop two stages (floor 0) and reschedule sooner. */
private fun LocalReviewProgress.demote(now: Instant, nextInterval: Duration): LocalReviewProgress = copy(
	reviewStage = maxOf(0, reviewStage - 2),
	dueAt = now.plus(nextInterval).toEpochMilli(),
	reviewCount = reviewCount + 1,
	failedReviews = failedReviews + 1,
	failureStreak = failureStreak + 1,
	lastFailureAt = now.toEpochMilli(),
)

/**
 * Verbatim-behavior Kotlin port of the backend's `WordProgressService`
 * (`backend/src/main/java/com/shikhi/practice/service/WordProgressService.java`, docs/
 * 93-offline-learning-design.md §4.3): updates one learner's word-level state after an answer is
 * graded — mastery (0..5), graduation onto the review ladder, and ladder transitions, all in a
 * single call so [LocalPracticeSource.grade] covers the whole update atomically per answer.
 *
 * Ladder rule (deviation #7, carried over verbatim): a correct answer only promotes the ladder
 * when the word was actually due — this is invoked on every answer, including ones served while a
 * word is not yet due, and those must not inflate future intervals. A late review still promotes
 * normally; a wrong answer always demotes, regardless of due status. Graduation is evaluated only
 * on a correct answer (a wrong answer never graduates a word — Fix 4 in the backend's history).
 */
class WordProgressEngine @Inject constructor(private val dao: WordProgressDao) {

	/** [now] is computed once by the caller and reused for both mastery and ladder updates —
	 * mirrors the backend reading its injected `Clock` once per `recordAnswer` call. */
	suspend fun recordAnswer(userId: String, vocabularyId: String, correct: Boolean, now: Instant = Instant.now()) {
		val nowMillis = now.toEpochMilli()

		val existingMastery = dao.getWordProgress(userId, vocabularyId)
			?: LocalWordProgress(userId = userId, vocabularyId = vocabularyId, lastSeenAt = nowMillis)
		val mastery = applyAnswer(existingMastery, correct, nowMillis)
		dao.upsert(mastery)

		val review = dao.getReviewProgress(userId, vocabularyId)
		if (review == null) {
			// Graduation only rewards a correct answer: evaluating the gate after a wrong answer
			// too would let a word enter the review ladder off the very mistake just made.
			if (correct && graduated(mastery)) {
				val stage = 1
				dao.upsertReview(
					LocalReviewProgress(
						userId = userId,
						vocabularyId = vocabularyId,
						reviewStage = stage,
						dueAt = now.plus(FixedIntervalScheduler.interval(stage)).toEpochMilli(),
					),
				)
			}
			return
		}

		if (correct) {
			// Non-due appearance: leave the ladder untouched.
			if (review.isDue(nowMillis)) {
				val newStage = review.reviewStage + 1
				dao.upsertReview(review.promote(now, FixedIntervalScheduler.interval(newStage)))
			}
		} else {
			val newStage = maxOf(0, review.reviewStage - 2)
			dao.upsertReview(review.demote(now, FixedIntervalScheduler.interval(newStage)))
		}
	}

	private fun applyAnswer(progress: LocalWordProgress, correct: Boolean, nowMillis: Long): LocalWordProgress = progress.copy(
		timesSeen = progress.timesSeen + 1,
		timesCorrect = progress.timesCorrect + if (correct) 1 else 0,
		timesWrong = progress.timesWrong + if (correct) 0 else 1,
		masteryScore = (progress.masteryScore + if (correct) 1 else -2).coerceIn(0, MAX_MASTERY),
		lastWrongAt = if (correct) progress.lastWrongAt else nowMillis,
		lastSeenAt = nowMillis,
	)

	/** Graduation gate: all three thresholds, evaluated after this answer — only reachable when
	 * the answer was correct (see the `correct &&` check at the call site above). */
	private fun graduated(mastery: LocalWordProgress): Boolean =
		mastery.masteryScore >= GRADUATION_MASTERY &&
			mastery.timesCorrect >= GRADUATION_TIMES_CORRECT &&
			mastery.timesSeen >= GRADUATION_TIMES_SEEN

	companion object {
		const val MAX_MASTERY = 5

		// Graduation-threshold defaults, verified against
		// backend/src/main/java/com/shikhi/practice/config/PlannerProperties.java.
		const val GRADUATION_MASTERY = 3
		const val GRADUATION_TIMES_CORRECT = 2
		const val GRADUATION_TIMES_SEEN = 3
	}
}

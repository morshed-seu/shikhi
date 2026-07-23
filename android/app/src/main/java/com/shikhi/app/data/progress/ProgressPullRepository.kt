package com.shikhi.app.data.progress

import androidx.room.withTransaction
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.CompletedLessonEntry
import com.shikhi.app.data.api.dto.ReviewProgressEntry
import com.shikhi.app.data.api.dto.WordProgressEntry
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.db.LocalLessonCompletion
import com.shikhi.app.data.db.LocalReviewProgress
import com.shikhi.app.data.db.LocalWordProgress
import com.shikhi.app.data.db.ShikhiDatabase
import com.shikhi.app.data.outbox.OutboxRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Download direction of sync (UO6, docs/95 §3.2/§5): rebuilds local mastery/review/lesson-
 * completion + the stats projection from the server's authoritative GET /v1/progress/snapshot —
 * for a fresh install or a 2nd device signing into an existing account. Gated on an empty outbox
 * ([outbox].flush() must succeed first) so this is a pure overwrite, never a per-row merge.
 */
@Singleton
class ProgressPullRepository @Inject constructor(
	private val progressApi: dagger.Lazy<ProgressApi>,
	private val outbox: OutboxRepository,
	private val db: ShikhiDatabase,
	private val projection: StatsProjectionRepository,
	private val authRepository: AuthRepository,
	private val tokenStore: TokenStore,
) {

	/** False signals the caller ([ProgressPullWorker]) should retry. */
	suspend fun pull(): Boolean {
		// A LocalGuest has no server-side account/token yet — nothing to pull, and calling the
		// snapshot API would 401. Matches OutboxRepository.currentUserId()'s own Active-only
		// assumption for reconcile.
		val userId = when (val state = authRepository.session.value) {
			is SessionState.Active -> state.user.id
			else -> return true
		}

		// UO6 (docs/95 §3.2): never overwrite local tables while un-synced work is pending.
		if (!outbox.flush()) return false

		return try {
			val snapshot = progressApi.get().snapshot()
			db.withTransaction {
				db.wordProgressDao().deleteAllForUser(userId)
				db.wordProgressDao().upsertAll(snapshot.wordProgress.map { it.toEntity(userId) })
				db.wordProgressDao().deleteAllReviewForUser(userId)
				db.wordProgressDao().upsertAllReview(snapshot.reviewProgress.map { it.toEntity(userId) })
				db.localLessonCompletionDao().deleteAllForUser(userId)
				db.localLessonCompletionDao().upsertAll(snapshot.completedLessons.map { it.toEntity(userId) })
				projection.reconcile(userId, snapshot.stats)
			}
			tokenStore.setLastSyncedAt(Instant.parse(snapshot.serverTime).toEpochMilli())
			true
		} catch (e: Exception) {
			false
		}
	}
}

private fun WordProgressEntry.toEntity(userId: String) = LocalWordProgress(
	userId = userId,
	vocabularyId = vocabularyId,
	timesSeen = timesSeen,
	timesCorrect = timesCorrect,
	timesWrong = timesWrong,
	masteryScore = masteryScore,
	lastWrongAt = lastWrongAt?.let { Instant.parse(it).toEpochMilli() },
	lastSeenAt = Instant.parse(lastSeenAt).toEpochMilli(),
)

private fun ReviewProgressEntry.toEntity(userId: String) = LocalReviewProgress(
	userId = userId,
	vocabularyId = vocabularyId,
	reviewStage = reviewStage,
	dueAt = Instant.parse(dueAt).toEpochMilli(),
	lastReviewedAt = lastReviewedAt?.let { Instant.parse(it).toEpochMilli() },
	reviewCount = reviewCount,
	successfulReviews = successfulReviews,
	failedReviews = failedReviews,
	failureStreak = failureStreak,
	lastFailureAt = lastFailureAt?.let { Instant.parse(it).toEpochMilli() },
)

// UO6: the snapshot has no equivalent of firstCompletionEventId (it pins a ledger row to the
// exact outbox event that first completed a lesson locally — see
// StatsProjectionRepository.pendingXpDelta's COMPLETE_LESSON branch). A pull-rebuilt row has no
// backing local event, so it's mapped to the sentinel -1L: OutboxEventEntity.id is
// @PrimaryKey(autoGenerate = true) starting at 1, so -1L never collides, and pendingXpDelta only
// compares firstCompletionEventId against events actually sitting in the outbox at the time —
// pull is gated on an empty outbox, so there's no pending COMPLETE_LESSON event to compare
// against at the moment of pull anyway. If the lesson is completed again offline afterward,
// LocalLessonSource.complete()'s own upsert (REPLACE strategy) overwrites this sentinel row with
// a real event id.
//
// contentVersionId is deliberately NOT taken from the snapshot: LocalLessonSource.complete()'s
// own "already completed" gate (and StatsProjectionRepository.pendingXpDelta's ledger lookup)
// always query with the local CONTENT_VERSION_ID sentinel (bundled content has no live version —
// see that file's own copy of this constant). Writing the server's real (UUID) contentVersionId
// here would land the pulled row in a different composite-PK slot, invisible to those lookups —
// a lesson completed on another device would silently fail to gate a same-lesson offline
// re-completion, double-counting its XP locally until the next reconcile overwrites it away.
private fun CompletedLessonEntry.toEntity(userId: String) = LocalLessonCompletion(
	userId = userId,
	lessonId = lessonId,
	contentVersionId = CONTENT_VERSION_ID,
	firstCompletionEventId = -1L,
	completedAt = System.currentTimeMillis(),
)

/** Matches [com.shikhi.app.data.lesson.LocalLessonSource]'s own `CONTENT_VERSION_ID` — duplicated
 * per this codebase's convention (see that file / StatsProjectionRepository's own copies). */
private const val CONTENT_VERSION_ID = ""

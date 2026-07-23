package com.shikhi.app.data.outbox

import androidx.room.withTransaction
import androidx.work.WorkManager
import com.shikhi.app.data.api.PracticeApi
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.SubmitAnswerRequest
import com.shikhi.app.data.api.dto.SyncBatchRequest
import com.shikhi.app.data.api.dto.SyncEvent
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.db.OutboxDao
import com.shikhi.app.data.db.OutboxEventEntity
import com.shikhi.app.data.db.ShikhiDatabase
import com.shikhi.app.data.progress.StatsProjectionRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Event types accepted by POST /progress/sync (web outbox.ts), plus OF4 additions. */
object OutboxEventType {
	const val COMPLETE_LESSON = "COMPLETE_LESSON"
	const val ANSWER = "ANSWER"

	/** OF4 (docs/93-offline-learning-design.md §5): a fully-local-graded practice answer, keyed
	 * by vocabulary id — replayed through the generic `/progress/sync` batch like [ANSWER]/
	 * [COMPLETE_LESSON]. Emitted only by [com.shikhi.app.data.practice.LocalPracticeSource]. */
	const val PRACTICE_ANSWER = "PRACTICE_ANSWER"

	/**
	 * OF4: a *remote* practice answer whose `submitAnswer` call failed for a recoverable reason
	 * (network/5xx) — emitted only by [com.shikhi.app.data.practice.RemotePracticeSource].
	 * Deliberately NOT shaped like [PRACTICE_ANSWER]: the client never learns the exercise's
	 * `vocabularyId` for a remote session (the wire `PracticeExercise` DTO doesn't carry it —
	 * grading is server-side by design), so it cannot synthesize a valid
	 * `{vocabularyId, correct, answeredAt}` event on failure. What it *can* safely do is retry the
	 * exact original request verbatim (idempotency key already minted) — [flush] special-cases
	 * this type and replays it through [PracticeApi.submitAnswer] directly instead of the generic
	 * `/progress/sync` batch, so grading (and the resulting mastery/review/XP/hearts update)
	 * happens for real once connectivity returns, rather than being silently dropped as before
	 * OF4.
	 */
	const val RETRY_PRACTICE_SUBMIT = "RETRY_PRACTICE_SUBMIT"

	/**
	 * UO3 (`~/.claude/plans/unified-offline-online/UO3.md`): a learner-initiated CEFR level
	 * change, emitted by [com.shikhi.app.data.progress.LevelRepository.setLevel] instead of the
	 * old network-only `PUT /stats/level`. Payload shape `{cefrLevel, changedAt}` matches exactly
	 * what UO1's backend `ProgressEventApplier` already expects. Contributes `0` to
	 * [com.shikhi.app.data.progress.StatsProjectionRepository.displayXp] — a level change never
	 * awards XP.
	 */
	const val SET_LEVEL = "SET_LEVEL"
}

/**
 * Offline outbox with the exact semantics of the web client (frontend/src/api/outbox.ts):
 * enqueue mints the idempotency key once; flush replays everything in one idempotent
 * batch and clears only on success. Flush triggers: app foreground and post-lesson
 * (WorkManager scheduling arrives in MA4).
 */
@Singleton
class OutboxRepository @Inject constructor(
	private val dao: OutboxDao,
	private val progressApi: dagger.Lazy<ProgressApi>,
	private val practiceApi: dagger.Lazy<PracticeApi>,
	private val workManager: dagger.Lazy<WorkManager>,
	private val db: ShikhiDatabase,
	private val projection: StatsProjectionRepository,
	private val authRepository: AuthRepository,
	private val tokenStore: TokenStore,
) {

	suspend fun enqueue(type: String, payload: JsonObject) {
		dao.insert(
			OutboxEventEntity(
				idempotencyKey = UUID.randomUUID().toString(),
				type = type,
				payloadJson = payload.toString(),
				createdAt = System.currentTimeMillis(),
			),
		)
		// A buffered event means we're (probably) offline: let WorkManager deliver it
		// with backoff as soon as connectivity returns.
		OutboxSyncWorker.schedule(workManager.get())
	}

	/**
	 * True when the outbox ends up empty (nothing to send, or everything was accepted).
	 * [OutboxEventType.RETRY_PRACTICE_SUBMIT] events replay individually against
	 * [PracticeApi.submitAnswer] (they aren't shaped for the generic sync batch — see that
	 * constant's doc); everything else still goes through one `/progress/sync` call, same as
	 * before OF4. A partial success (some retries land, the sync batch doesn't, or vice versa)
	 * clears only what succeeded and reports `false` so the worker retries the rest.
	 */
	suspend fun flush(): Boolean {
		val events = dao.all()
		if (events.isEmpty()) return true
		val (retries, syncable) = events.partition { it.type == OutboxEventType.RETRY_PRACTICE_SUBMIT }

		val succeeded = mutableListOf<Long>()
		var allOk = true
		// UO2: only a successful `/progress/sync` batch yields a fresh Stats to reconcile with —
		// a retries-only flush (syncable empty) has nothing to collapse into the baseline.
		var stats: Stats? = null

		for (event in retries) {
			try {
				val payload = Json.parseToJsonElement(event.payloadJson).jsonObject
				practiceApi.get().submitAnswer(
					sessionId = payload.getValue("sessionId").jsonPrimitive.content,
					body = SubmitAnswerRequest(
						idempotencyKey = payload.getValue("idempotencyKey").jsonPrimitive.content,
						exerciseId = payload.getValue("exerciseId").jsonPrimitive.content,
						answer = payload.getValue("answer").jsonObject,
					),
				)
				succeeded += event.id
			} catch (e: Exception) {
				allOk = false
			}
		}

		if (syncable.isNotEmpty()) {
			try {
				stats = progressApi.get().sync(
					SyncBatchRequest(
						syncable.map {
							SyncEvent(
								idempotencyKey = it.idempotencyKey,
								type = it.type,
								payload = Json.parseToJsonElement(it.payloadJson).jsonObject,
							)
						},
					),
				)
				succeeded += syncable.map { it.id }
			} catch (e: Exception) {
				allOk = false
			}
		}

		// UO2 (docs/95-unified-offline-online-design.md §3.1): delete-the-synced-rows and
		// collapse-them-into-the-baseline must happen in ONE Room transaction — that's what makes
		// double-counting structurally impossible (a delta lives in *pending* XOR *baseline*,
		// never both, even if the process dies between the two writes).
		if (succeeded.isNotEmpty()) {
			db.withTransaction {
				dao.deleteByIds(succeeded)
				stats?.let { projection.reconcile(currentUserId(), it) }
			}
		}
		return allOk
	}

	/**
	 * The outbox table has no userId column (flush runs under whatever bearer token/local-guest
	 * id is currently active — see this class's doc), so reconcile needs its own read of the
	 * active user, same logic as [com.shikhi.app.data.practice.LocalPracticeSource.currentUserId].
	 */
	private suspend fun currentUserId(): String = when (val state = authRepository.session.value) {
		is SessionState.Active -> state.user.id
		is SessionState.LocalGuest -> tokenStore.localGuestId()
			?: error("LocalGuest session with no stored localGuestId — invariant violated")
		else -> error("Outbox flush reconcile requires an already-authenticated or local-guest session")
	}
}

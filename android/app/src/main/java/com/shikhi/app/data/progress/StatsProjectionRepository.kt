package com.shikhi.app.data.progress

import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.db.LocalStatsProjection
import com.shikhi.app.data.db.LocalStatsProjectionDao
import com.shikhi.app.data.db.OutboxDao
import com.shikhi.app.data.db.OutboxEventEntity
import com.shikhi.app.data.outbox.OutboxEventType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UO2 (docs/95-unified-offline-online-design.md §4, ADR-0015 — see also
 * `~/.claude/plans/unified-offline-online/00-shared-context.md`'s "reconciliation model"): the
 * durable on-device stats projection. Nothing reads [displayXp] yet — that lands in `UO4`/`UO6`;
 * this gate only builds the projection + wires [reconcile] into
 * [com.shikhi.app.data.outbox.OutboxRepository.flush] so double-counting is structurally
 * impossible from day one.
 *
 * XP is deliberately never stored as a live counter (§3.1: only the server recomputes XP from
 * replayed primitive events) — [displayXp] always recomputes `baselineXp + Σ pendingXpDelta` at
 * read time from whatever is still sitting in the outbox. `hearts`/streak/etc are the opposite:
 * non-additive, so they are simply overwritten wholesale by [reconcile] from the `Stats` the
 * server already returns on every successful sync.
 */
@Singleton
class StatsProjectionRepository @Inject constructor(
	private val dao: LocalStatsProjectionDao,
	private val outboxDao: OutboxDao,
) {

	/** Idempotent no-op if a row already exists — safe to call on every session start. */
	suspend fun ensureRow(userId: String) {
		if (dao.get(userId) != null) return
		val defaults = Stats()
		dao.upsert(
			LocalStatsProjection(
				userId = userId,
				baselineXp = defaults.xp,
				hearts = defaults.hearts,
				currentStreak = defaults.currentStreak,
				longestStreak = defaults.longestStreak,
				cefrLevel = defaults.cefrLevel,
				lastActiveDate = null,
				rank = defaults.rank,
				dailyGoal = defaults.dailyGoal,
				updatedAt = System.currentTimeMillis(),
			),
		)
	}

	/**
	 * `baselineXp + Σ pendingXpDelta(every outbox row)`. Deliberately tolerant of [ensureRow]
	 * never having been called (baseline defaults to 0) — a fresh install with no reconcile yet
	 * should not crash, just read as "0 + whatever's pending".
	 */
	suspend fun displayXp(userId: String): Int {
		val baseline = dao.get(userId)?.baselineXp ?: 0
		return baseline + outboxDao.all().sumOf { pendingXpDelta(it) }
	}

	/**
	 * Overwrites the row wholesale from server truth: `baselineXp` collapses whatever XP the
	 * server has already accounted for (including the events [flush][com.shikhi.app.data.outbox.OutboxRepository.flush]
	 * is about to delete in the SAME transaction as this call — that atomicity is what prevents a
	 * pending delta from ever being counted twice). `lastActiveDate` has no source in [Stats] yet,
	 * so it is left untouched here — not this gate's concern.
	 */
	suspend fun reconcile(userId: String, stats: Stats) {
		val existing = dao.get(userId)
		dao.upsert(
			LocalStatsProjection(
				userId = userId,
				baselineXp = stats.xp,
				hearts = stats.hearts,
				currentStreak = stats.currentStreak,
				longestStreak = stats.longestStreak,
				cefrLevel = stats.cefrLevel,
				lastActiveDate = existing?.lastActiveDate,
				rank = stats.rank,
				dailyGoal = stats.dailyGoal,
				updatedAt = System.currentTimeMillis(),
			),
		)
	}

	/**
	 * UO3: optimistically overwrites just [LocalStatsProjection.cefrLevel] for a local
	 * (not-yet-synced) level change, preserving every other field of the existing row — unlike
	 * [reconcile], this is NOT server truth yet, just the learner's own in-progress choice.
	 * Seeds a fresh row via [ensureRow] first so this is safe to call even before the projection
	 * has ever been reconciled (e.g. a brand-new guest changing level before their first sync).
	 */
	suspend fun setLevel(userId: String, level: String) {
		ensureRow(userId)
		val existing = dao.get(userId)!!
		dao.upsert(existing.copy(cefrLevel = level, updatedAt = System.currentTimeMillis()))
	}

	/**
	 * Decodes one outbox event's contribution to un-synced XP. `COMPLETE_LESSON` -> `score * 10`
	 * for every event (first-completion gating against already-awarded XP is deferred to `UO4` —
	 * not implemented here). `PRACTICE_ANSWER` -> `+10` only when `correct == true`. Everything
	 * else (`ANSWER`, `SET_LEVEL`, `RETRY_PRACTICE_SUBMIT`, and any future type) contributes `0` —
	 * the `else` branch is deliberately defensive so an unrecognized/future event type never
	 * silently inflates displayed XP.
	 */
	private fun pendingXpDelta(event: OutboxEventEntity): Int = when (event.type) {
		OutboxEventType.PRACTICE_ANSWER -> {
			val payload = Json.parseToJsonElement(event.payloadJson).jsonObject
			// +10 per correct answer — must match LocalPracticeSource.XP_PER_CORRECT.
			if (payload.getValue("correct").jsonPrimitive.boolean) XP_PER_CORRECT else 0
		}
		OutboxEventType.COMPLETE_LESSON -> {
			val payload = Json.parseToJsonElement(event.payloadJson).jsonObject
			payload.getValue("score").jsonPrimitive.int * XP_PER_CORRECT
		}
		else -> 0
	}

	private companion object {
		/** Matches `LocalPracticeSource.XP_PER_CORRECT` / the backend's `ProgressService.XP_PER_CORRECT`. */
		const val XP_PER_CORRECT = 10
	}
}

package com.shikhi.app.data.progress

import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.db.LocalLessonCompletionDao
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
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UO2 (docs/95-unified-offline-online-design.md §4, ADR-0015 — see also
 * `~/.claude/plans/unified-offline-online/00-shared-context.md`'s "reconciliation model"): the
 * durable on-device stats projection.
 *
 * UO4 extends this from a pure sync-time projection into the live source the UI reads offline:
 * [registerActiveDay]/[loseHeart] port the backend's `UserStats` semantics (see that class) so
 * hearts/streak advance locally exactly like the server would, [currentHearts]/[overlay] are the
 * read paths callers use to display them, and [hasReconciled] drives the "never actually synced"
 * banner. [reconcile] remains the sole place non-additive fields are overwritten wholesale from
 * server truth — the local mutations below never race it because they only ever run inside a
 * single-writer local-play call, never concurrently with a sync.
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
	private val lessonCompletionDao: LocalLessonCompletionDao,
) {

	/** Idempotent no-op if a row already exists — safe to call on every session start. */
	suspend fun ensureRow(userId: String) {
		if (dao.get(userId) != null) return
		val defaults = Stats()
		dao.upsert(
			LocalStatsProjection(
				userId = userId,
				baselineXp = defaults.xp,
				// UO4 fix: a fresh account's true starting hearts is MAX_HEARTS, not
				// defaults.hearts (Stats()'s hearts == 0, which is only a wire-decode fallback for
				// a missing field, not a real starting value) — this was a latent bug from UO2
				// that only matters now that hearts are genuinely live/displayed offline.
				hearts = MAX_HEARTS,
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
		return baseline + outboxDao.all().sumOf { pendingXpDelta(userId, it) }
	}

	/**
	 * Overwrites the row wholesale from server truth: `baselineXp` collapses whatever XP the
	 * server has already accounted for (including the events [flush][com.shikhi.app.data.outbox.OutboxRepository.flush]
	 * is about to delete in the SAME transaction as this call — that atomicity is what prevents a
	 * pending delta from ever being counted twice). `lastActiveDate` has no source in [Stats] yet,
	 * so it is left untouched here — not this gate's concern. [reconciledAt][LocalStatsProjection.reconciledAt]
	 * is stamped with the current time on every call — it's the durable "has genuinely synced"
	 * marker [hasReconciled] reads.
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
				reconciledAt = System.currentTimeMillis(),
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
	 * UO4: local port of the backend's `UserStats.registerActiveDay` — see that class for the
	 * semantics this mirrors exactly (first activity of a new UTC day advances the streak, or
	 * resets it to 1 if a day was missed, and refills hearts). Idempotent within a day, so it is
	 * safe to call on every practice answer and lesson completion.
	 */
	suspend fun registerActiveDay(userId: String, today: LocalDate) {
		ensureRow(userId)
		val existing = dao.get(userId)!!
		if (today.toString() == existing.lastActiveDate) return

		val lastActiveDate = existing.lastActiveDate
		val newStreak = if (lastActiveDate != null && LocalDate.parse(lastActiveDate).plusDays(1) == today) {
			existing.currentStreak + 1
		} else {
			1
		}
		dao.upsert(
			existing.copy(
				currentStreak = newStreak,
				longestStreak = maxOf(existing.longestStreak, newStreak),
				lastActiveDate = today.toString(),
				hearts = MAX_HEARTS,
				updatedAt = System.currentTimeMillis(),
			),
		)
	}

	/** UO4: local port of `UserStats.loseHeart` — floors at 0, never goes negative. */
	suspend fun loseHeart(userId: String) {
		ensureRow(userId)
		val existing = dao.get(userId)!!
		if (existing.hearts <= 0) return
		dao.upsert(existing.copy(hearts = existing.hearts - 1, updatedAt = System.currentTimeMillis()))
	}

	/** UO4: the read path local play sources use to report live hearts, seeding a fresh row if
	 * this is the very first local activity for [userId]. */
	suspend fun currentHearts(userId: String): Int {
		ensureRow(userId)
		return dao.get(userId)!!.hearts
	}

	/** UO4: true once the projection has genuinely synced at least once — the marker
	 * [com.shikhi.app.ui.profile.ProfileViewModel] uses to decide whether `OfflineCopyBanner`
	 * should show (distinct from a given fetch merely being served from the Room cache). */
	suspend fun hasReconciled(userId: String): Boolean = dao.get(userId)?.reconciledAt != null

	/**
	 * UO4: overlays the live local projection's XP/hearts/streak/cefrLevel onto an otherwise-stale
	 * cached [Stats] (dashboard/profile/home hero) so offline play shows up immediately instead of
	 * only after the next sync. A no-op passthrough when there is no local projection row to
	 * overlay from (nothing local has happened yet for this user).
	 */
	suspend fun overlay(userId: String, stats: Stats): Stats {
		val row = dao.get(userId) ?: return stats
		return stats.copy(
			xp = displayXp(userId),
			hearts = row.hearts,
			currentStreak = row.currentStreak,
			longestStreak = row.longestStreak,
			cefrLevel = row.cefrLevel,
		)
	}

	/**
	 * Decodes one outbox event's contribution to un-synced XP. `PRACTICE_ANSWER` -> `+10` only
	 * when `correct == true`. `COMPLETE_LESSON` -> `score * XP_PER_CORRECT` ONLY when [event] is
	 * the exact event that first completed this lesson (per the [lessonCompletionDao] ledger —
	 * UO4) — a repeat completion (the ledger row's `firstCompletionEventId` points at an earlier
	 * event) contributes `0`, mirroring the server's first-completion-only XP rule so reconcile
	 * stays exact. Everything else (`ANSWER`, `SET_LEVEL`, `RETRY_PRACTICE_SUBMIT`, and any future
	 * type) contributes `0` — the `else` branch is deliberately defensive so an unrecognized/future
	 * event type never silently inflates displayed XP.
	 */
	private suspend fun pendingXpDelta(userId: String, event: OutboxEventEntity): Int = when (event.type) {
		OutboxEventType.PRACTICE_ANSWER -> {
			val payload = Json.parseToJsonElement(event.payloadJson).jsonObject
			// +10 per correct answer — must match LocalPracticeSource.XP_PER_CORRECT.
			if (payload.getValue("correct").jsonPrimitive.boolean) XP_PER_CORRECT else 0
		}
		OutboxEventType.COMPLETE_LESSON -> {
			val payload = Json.parseToJsonElement(event.payloadJson).jsonObject
			val lessonId = payload.getValue("lessonId").jsonPrimitive.content
			val score = payload.getValue("score").jsonPrimitive.int
			val ledgerRow = lessonCompletionDao.get(userId, lessonId, CONTENT_VERSION_ID)
			if (ledgerRow?.firstCompletionEventId == event.id) score * XP_PER_CORRECT else 0
		}
		else -> 0
	}

	private companion object {
		/** Matches `LocalPracticeSource.XP_PER_CORRECT` / the backend's `ProgressService.XP_PER_CORRECT`. */
		const val XP_PER_CORRECT = 10

		/** Matches the backend's `UserStats.MAX_HEARTS`. */
		const val MAX_HEARTS = 5

		/** Bundled content has no live contentVersion — matches `LessonView.contentVersion`'s
		 * existing blank convention (OF2), and [com.shikhi.app.data.lesson.LocalLessonSource]'s
		 * own `CONTENT_VERSION_ID`. */
		const val CONTENT_VERSION_ID = ""
	}
}

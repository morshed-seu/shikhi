package com.shikhi.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * UO2 (docs/95-unified-offline-online-design.md §4, ADR-0015): a durable, per-user on-device
 * stats projection — NOT a cache of the last-seen `Stats` response, and NOT a live local counter
 * (§3.1 rejects that: only the server recomputes XP from replayed events). [baselineXp] is the
 * last-reconciled `Stats.xp`; the learner's true current XP is always computed at read time as
 * `baselineXp + Σ pendingXpDelta(outbox rows)` (see
 * [com.shikhi.app.data.progress.StatsProjectionRepository.displayXp]), never stored here directly.
 * `hearts`/`currentStreak`/`longestStreak`/`rank`/`dailyGoal`/`cefrLevel` are non-additive and are
 * overwritten wholesale from server truth on every reconcile (the shared reconciliation model —
 * see `~/.claude/plans/unified-offline-online/00-shared-context.md`).
 *
 * `lastActiveDate` has no source yet ([com.shikhi.app.data.api.dto.Stats] doesn't carry it) — the
 * column exists now so the eventual `UO4`/`UO6` writer doesn't need another migration, but nothing
 * populates it in this gate.
 */
@Entity(tableName = "local_stats_projection")
data class LocalStatsProjection(
	@PrimaryKey val userId: String,
	val baselineXp: Int,
	val hearts: Int,
	val currentStreak: Int,
	val longestStreak: Int,
	val cefrLevel: String,
	val lastActiveDate: String?,
	val rank: Int,
	val dailyGoal: Int,
	val updatedAt: Long,
)

@Dao
interface LocalStatsProjectionDao {

	@Query("SELECT * FROM local_stats_projection WHERE userId = :userId")
	suspend fun get(userId: String): LocalStatsProjection?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(row: LocalStatsProjection)

	// OG2/UO2: same re-key pattern as WordProgressDao.rekey/LocalPracticeSessionDao.rekey — runs
	// inside GuestRegistrationWorker's existing db.withTransaction so a guest's stats projection
	// survives LocalGuest -> Active alongside its mastery/review/session rows.
	@Query("UPDATE local_stats_projection SET userId = :newUserId WHERE userId = :oldUserId")
	suspend fun rekey(oldUserId: String, newUserId: String)
}

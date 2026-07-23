package com.shikhi.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * A buffered progress event awaiting sync — the Room equivalent of the web outbox
 * (frontend/src/api/outbox.ts). The idempotency key is minted once at enqueue time and
 * reused for every flush attempt, so the server applies a replayed event at most once.
 */
@Entity(tableName = "outbox_events")
data class OutboxEventEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val idempotencyKey: String,
	val type: String,
	val payloadJson: String,
	val createdAt: Long,
)

@Dao
interface OutboxDao {
	@Insert
	suspend fun insert(event: OutboxEventEntity)

	@Query("SELECT * FROM outbox_events ORDER BY id")
	suspend fun all(): List<OutboxEventEntity>

	@Query("DELETE FROM outbox_events WHERE id IN (:ids)")
	suspend fun deleteByIds(ids: List<Long>)
}

@Database(
	entities = [
		OutboxEventEntity::class,
		CachedPayload::class,
		LocalWordProgress::class,
		LocalReviewProgress::class,
		LocalPracticeSession::class,
		LocalPracticeExercise::class,
		LocalStatsProjection::class,
	],
	version = 4,
	exportSchema = false,
)
abstract class ShikhiDatabase : RoomDatabase() {
	abstract fun outboxDao(): OutboxDao

	abstract fun contentCacheDao(): ContentCacheDao

	/** OF4: local practice/mastery state — see [MIGRATION_2_3]. */
	abstract fun wordProgressDao(): WordProgressDao

	abstract fun localPracticeSessionDao(): LocalPracticeSessionDao

	/** UO2: durable per-user stats projection — see [MIGRATION_3_4]. */
	abstract fun localStatsProjectionDao(): LocalStatsProjectionDao
}

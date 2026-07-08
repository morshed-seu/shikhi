package com.shikhi.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Offline content cache (MA4): whole API payloads stored as JSON, keyed by resource
 * ("curriculum", "stats", "vocab/A1"). Grading stays server-side, so this powers offline
 * BROWSING (curriculum, stats, word lists) — not offline lesson play (PRD 21 §Phase C).
 */
@Entity(tableName = "content_cache")
data class CachedPayload(
	@PrimaryKey val key: String,
	val json: String,
	val updatedAt: Long,
)

@Dao
interface ContentCacheDao {
	@Query("SELECT * FROM content_cache WHERE `key` = :key")
	suspend fun get(key: String): CachedPayload?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun put(payload: CachedPayload)
}

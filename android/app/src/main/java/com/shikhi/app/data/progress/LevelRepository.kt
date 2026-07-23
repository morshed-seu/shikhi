package com.shikhi.app.data.progress

import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ContentCacheDao
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UO3 (`~/.claude/plans/unified-offline-online/UO3.md`): lets a learner change their CEFR level
 * while offline. Kept as its own small class rather than folded into
 * [StatsProjectionRepository] or [OutboxRepository] because it sits ACROSS both: it needs to
 * write the durable projection row (UO2), the `"stats"` `content_cache` blob (so
 * [com.shikhi.app.data.practice.LocalPracticeSource.cachedCefrLevel] honours the change on the
 * very next offline session), and enqueue the outbox event — [OutboxRepository] already injects
 * [StatsProjectionRepository], so [OutboxRepository] injecting this class (or vice versa) would
 * be circular. [StatsProjectionRepository] keeps owning all `local_stats_projection` table access
 * (this class delegates to [StatsProjectionRepository.setLevel] rather than taking
 * [com.shikhi.app.data.db.LocalStatsProjectionDao] directly).
 *
 * There is deliberately no online/offline branch here — [setLevel] always performs the same three
 * local writes; [OutboxRepository]'s sync worker delivers the [OutboxEventType.SET_LEVEL] event to
 * the server whenever connectivity allows, exactly mirroring how
 * [com.shikhi.app.data.practice.LocalPracticeSource]'s `PRACTICE_ANSWER` events already work.
 */
@Singleton
class LevelRepository @Inject constructor(
	private val statsProjectionRepository: StatsProjectionRepository,
	private val cacheDao: ContentCacheDao,
	private val outboxRepository: OutboxRepository,
	private val authRepository: AuthRepository,
	private val tokenStore: TokenStore,
) {

	private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

	suspend fun setLevel(level: String) {
		val userId = currentUserId()

		// 1. Durable projection row (UO2) — read-modify-write via StatsProjectionRepository so
		// every other field of the row survives untouched.
		statsProjectionRepository.setLevel(userId, level)

		// 2. The "stats" content_cache blob, so LocalPracticeSource.cachedCefrLevel() reads the
		// new level on the very next offline practice session (same tolerant decode pattern as
		// that method: fall back to Stats() defaults on a miss or a corrupt cache entry).
		val cached = cacheDao.get(STATS_CACHE_KEY)
		val stats = cached
			?.let { runCatching { json.decodeFromString(Stats.serializer(), it.json) }.getOrNull() }
			?: Stats()
		cacheDao.put(
			CachedPayload(
				key = STATS_CACHE_KEY,
				json = json.encodeToString(Stats.serializer(), stats.copy(cefrLevel = level)),
				updatedAt = System.currentTimeMillis(),
			),
		)

		// 3. Buffer the sync event — delivered by OutboxSyncWorker whenever connectivity allows.
		// Payload shape must match ProgressEventApplier's SET_LEVEL handling exactly (UO1).
		outboxRepository.enqueue(
			OutboxEventType.SET_LEVEL,
			buildJsonObject {
				put("cefrLevel", level)
				put("changedAt", Instant.now().toString())
			},
		)
	}

	/**
	 * Same shape as the identically-named private helpers on
	 * [com.shikhi.app.data.practice.LocalPracticeSource] and [OutboxRepository] — deliberately
	 * duplicated per-class in this codebase rather than shared (see those methods' doc comments).
	 */
	private suspend fun currentUserId(): String = when (val state = authRepository.session.value) {
		is SessionState.Active -> state.user.id
		is SessionState.LocalGuest -> tokenStore.localGuestId()
			?: error("LocalGuest session with no stored localGuestId — invariant violated")
		else -> error("Level change requires an already-authenticated or local-guest session")
	}

	private companion object {
		const val STATS_CACHE_KEY = "stats"
	}
}

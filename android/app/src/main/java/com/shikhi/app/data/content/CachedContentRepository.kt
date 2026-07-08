package com.shikhi.app.data.content

import com.shikhi.app.data.api.ContentApi
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.VocabularyApi
import com.shikhi.app.data.api.dto.CurriculumTree
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.VocabularyEntry
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ContentCacheDao
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A payload plus where it came from — the UI shows stale data with an offline hint. */
data class Sourced<T>(val value: T, val fromCache: Boolean)

/**
 * Network-first content reads with a Room JSON cache as the offline fallback (MA4,
 * NFR-AN4): a successful fetch refreshes the cache; a failed one serves the last good
 * payload if there is one. Curriculum, stats, and vocabulary only — lesson play needs
 * the network (grading is server-side).
 */
@Singleton
class CachedContentRepository @Inject constructor(
	private val contentApi: ContentApi,
	private val progressApi: ProgressApi,
	private val vocabularyApi: VocabularyApi,
	private val cache: ContentCacheDao,
) {

	private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

	suspend fun curriculum(): Sourced<CurriculumTree>? =
		fetch("curriculum", CurriculumTree.serializer()) { contentApi.curriculum() }

	suspend fun stats(): Sourced<Stats>? =
		fetch("stats", Stats.serializer()) { progressApi.stats() }

	suspend fun vocabulary(level: String): Sourced<List<VocabularyEntry>>? =
		fetch("vocab/$level", ListSerializer(VocabularyEntry.serializer())) { vocabularyApi.list(level) }

	private suspend fun <T> fetch(
		key: String,
		serializer: KSerializer<T>,
		network: suspend () -> T,
	): Sourced<T>? {
		try {
			val fresh = network()
			cache.put(CachedPayload(key, json.encodeToString(serializer, fresh), System.currentTimeMillis()))
			return Sourced(fresh, fromCache = false)
		} catch (e: Exception) {
			val cached = cache.get(key) ?: return null
			val value = runCatching { json.decodeFromString(serializer, cached.json) }.getOrNull() ?: return null
			return Sourced(value, fromCache = true)
		}
	}
}

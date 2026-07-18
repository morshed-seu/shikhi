package com.shikhi.app.data.content

import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.Bilingual
import com.shikhi.app.data.api.dto.CurriculumTree
import com.shikhi.app.data.api.dto.LessonNode
import com.shikhi.app.data.api.dto.LevelNode
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.UnitNode
import com.shikhi.app.data.api.dto.VocabularyEntry
import com.shikhi.app.data.content.db.ContentReadDao
import com.shikhi.app.data.content.db.LocalVocabulary
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ContentCacheDao
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A payload plus where it came from — the UI shows stale data with an offline hint. */
data class Sourced<T>(val value: T, val fromCache: Boolean)

/**
 * Content reads for the Android client (OF2, docs/93-offline-learning-design.md §3.2/§3.3).
 *
 * `curriculum()` and `vocabulary(level)` read the bundled, always-on-device [ContentReadDao]
 * (OF1) — zero network calls, so both wrap their result as `Sourced(value, fromCache = false)`:
 * the payload isn't "a cached copy of something fetched," it *is* the shipped source of truth,
 * same as any other bundled app asset. This also means [com.shikhi.app.ui.home.HomeScreen]'s
 * "offline copy" banner (driven by `fromCache`) simply stops firing for curriculum — correct,
 * since there's no more online/offline distinction to flag for content that never leaves the
 * device.
 *
 * "Not yet seeded" cold start (`ContentSeedWorker` hasn't finished importing yet — e.g. the
 * first second after a fresh install, before its `WorkManager` job completes): both bundled
 * reads return `null` on an empty table, exactly matching the old network-path's "fetch failed,
 * nothing cached" contract, so `VocabViewModel`/`HomeViewModel`'s existing error handling needs
 * no changes.
 *
 * `stats()` is a genuine server aggregate (§3.2, "stats() is unaffected") and keeps its
 * original network-first, Room-cache-fallback shape unchanged.
 */
@Singleton
class CachedContentRepository @Inject constructor(
	private val progressApi: ProgressApi,
	private val cache: ContentCacheDao,
	private val contentDao: ContentReadDao,
) {

	private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

	suspend fun curriculum(): Sourced<CurriculumTree>? {
		val levels = contentDao.getLevels()
		if (levels.isEmpty()) return null
		val levelNodes = levels.map { level ->
			val units = contentDao.getUnitsForLevel(level.id).map { unit ->
				val lessons = contentDao.getLessonsForUnit(unit.id).map { lesson ->
					LessonNode(
						id = lesson.id,
						title = Bilingual(lesson.titleEn, lesson.titleBn),
						ordinal = lesson.ordinal,
						// status/locked are a server-side per-learner progress overlay (§4.1),
						// not carried by the bundled LocalLesson row — out of scope for OF2, so
						// left at their DTO defaults (NOT_STARTED / unlocked).
					)
				}
				UnitNode(
					id = unit.id,
					code = unit.code,
					title = Bilingual(unit.titleEn, unit.titleBn),
					ordinal = unit.ordinal,
					lessons = lessons,
				)
			}
			LevelNode(
				id = level.id,
				code = level.code,
				title = Bilingual(level.titleEn, level.titleBn),
				ordinal = level.ordinal,
				units = units,
			)
		}
		return Sourced(CurriculumTree(levels = levelNodes), fromCache = false)
	}

	suspend fun stats(): Sourced<Stats>? =
		fetch("stats", Stats.serializer()) { progressApi.stats() }

	suspend fun vocabulary(level: String): Sourced<List<VocabularyEntry>>? {
		val rows = contentDao.getVocabularyByLevel(level)
		if (rows.isEmpty()) return null
		return Sourced(rows.map { it.toVocabularyEntry() }, fromCache = false)
	}

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

private fun LocalVocabulary.toVocabularyEntry() = VocabularyEntry(
	id = id,
	headword = headword,
	senseLabel = senseLabel,
	partOfSpeech = partOfSpeech,
	cefrLevel = cefrLevel,
	bnGloss = bnGloss,
	exampleEn = exampleEn,
	exampleBn = exampleBn,
)

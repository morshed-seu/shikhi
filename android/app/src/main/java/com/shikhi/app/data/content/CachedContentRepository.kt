package com.shikhi.app.data.content

import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.Bilingual
import com.shikhi.app.data.api.dto.CurriculumTree
import com.shikhi.app.data.api.dto.LessonNode
import com.shikhi.app.data.api.dto.LevelNode
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.UnitNode
import com.shikhi.app.data.api.dto.VocabularyEntry
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.content.db.ContentReadDao
import com.shikhi.app.data.content.db.LocalVocabulary
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ContentCacheDao
import com.shikhi.app.data.progress.StatsProjectionRepository
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
	private val statsProjectionRepository: StatsProjectionRepository,
	private val authRepository: AuthRepository,
	private val tokenStore: TokenStore,
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

	/**
	 * UO4: a cached/offline `stats()` response is stale for XP/hearts/streak — overlay the live
	 * local projection so [com.shikhi.app.ui.home.HomeViewModel]'s hero reflects offline play
	 * immediately instead of only after the next sync. [runCatching] is defensive because
	 * [currentUserId] can throw outside an authenticated/local-guest session — never let that sink
	 * an otherwise-good cached read.
	 */
	suspend fun stats(): Sourced<Stats>? {
		val result = fetch("stats", Stats.serializer()) { progressApi.stats() }
		if (result == null || !result.fromCache) return result
		val overlaid = runCatching { statsProjectionRepository.overlay(currentUserId(), result.value) }
			.getOrDefault(result.value)
		return Sourced(overlaid, fromCache = true)
	}

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

	/**
	 * Same shape as [com.shikhi.app.data.practice.LocalPracticeSource]'s / [com.shikhi.app.data.progress.LevelRepository]'s
	 * identically-named private helpers — deliberately duplicated per-class in this codebase
	 * rather than shared (see those methods' doc comments).
	 */
	private suspend fun currentUserId(): String = when (val state = authRepository.session.value) {
		is SessionState.Active -> state.user.id
		is SessionState.LocalGuest -> tokenStore.localGuestId()
			?: error("LocalGuest session with no stored localGuestId — invariant violated")
		else -> error("Stats overlay requires an already-authenticated or local-guest session")
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

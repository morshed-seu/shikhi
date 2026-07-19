package com.shikhi.app.data.practice

import com.shikhi.app.data.api.dto.Bilingual
import com.shikhi.app.data.api.dto.CEFR_LEVELS
import com.shikhi.app.data.api.dto.ChoiceOption
import com.shikhi.app.data.api.dto.PracticeExercise
import com.shikhi.app.data.api.dto.PracticeExerciseConfig
import com.shikhi.app.data.api.dto.PracticeResult
import com.shikhi.app.data.api.dto.PracticeRound
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.content.db.LocalVocabulary
import com.shikhi.app.data.db.ContentCacheDao
import com.shikhi.app.data.db.LocalPracticeExercise
import com.shikhi.app.data.db.LocalPracticeSession
import com.shikhi.app.data.db.LocalPracticeSessionDao
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline practice play (OF4, docs/93-offline-learning-design.md §3.3/§4.3/§5): bundled vocabulary
 * ([PracticeWordPicker]) + local generation ([PracticeGenerator]) + local grading
 * ([PracticeGrading]) + local mastery/review tracking ([WordProgressEngine]) + an
 * outbox-buffered [OutboxEventType.PRACTICE_ANSWER] event per graded answer. No network calls
 * anywhere in this class, mirroring OF3's [com.shikhi.app.data.lesson.LocalLessonSource].
 *
 * Round composition is the legacy (pre-VE4) picker only — the daily-planner layer
 * (`PlanRoundComposer`/`practice.policy.*`) is out of scope for this gate (design doc §4.3, §7:
 * disabled in prod today).
 */
@Singleton
class LocalPracticeSource @Inject constructor(
	private val picker: PracticeWordPicker,
	private val sessionDao: LocalPracticeSessionDao,
	private val wordProgressEngine: WordProgressEngine,
	private val outbox: OutboxRepository,
	private val cacheDao: ContentCacheDao,
	private val authRepository: AuthRepository,
	private val tokenStore: TokenStore,
) : PracticePlaySource {

	private val generator = PracticeGenerator()
	private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

	override suspend fun start(): PracticeRound {
		val userId = currentUserId()
		val level = cachedCefrLevel()
		val sessionId = UUID.randomUUID().toString()
		sessionDao.upsertSession(
			LocalPracticeSession(
				id = sessionId,
				userId = userId,
				cefrLevel = level,
				status = STATUS_IN_PROGRESS,
				roundsPlayed = 1,
				startedAt = System.currentTimeMillis(),
			),
		)
		return buildRound(sessionId, userId, level, round = 1, usedIds = emptySet())
	}

	override suspend fun nextRound(sessionId: String): PracticeRound {
		val session = sessionDao.getSession(sessionId)
			?: throw NoSuchElementException("Unknown local practice session: $sessionId")
		check(session.status != STATUS_COMPLETED) { "Session $sessionId is already completed" }
		val used = sessionDao.usedVocabularyIds(sessionId).toSet()
		val round = session.roundsPlayed + 1
		sessionDao.upsertSession(session.copy(roundsPlayed = round))
		return buildRound(sessionId, session.userId, session.cefrLevel, round, used)
	}

	override suspend fun grade(sessionId: String, exercise: PracticeExercise, answer: JsonObject): PracticeGradeOutcome {
		val row = sessionDao.getExercise(exercise.id)
			?: throw NoSuchElementException("Unknown local practice exercise: ${exercise.id}")
		val session = sessionDao.getSession(sessionId)
			?: throw NoSuchElementException("Unknown local practice session: $sessionId")
		val answerKey = json.parseToJsonElement(row.answerKeyJson).jsonObject

		val correct = PracticeGrading.grade(row.type, answer, answerKey)
		sessionDao.markAnswered(row.id, correct)
		sessionDao.upsertSession(
			session.copy(
				correctCount = session.correctCount + if (correct) 1 else 0,
				totalCount = session.totalCount + 1,
			),
		)

		val now = Instant.now()
		wordProgressEngine.recordAnswer(session.userId, row.vocabularyId, correct, now)

		// §5: the local path knows the vocabularyId and the (locally computed) correctness, so it
		// can emit the real PRACTICE_ANSWER event — unlike RemotePracticeSource's failure path,
		// which can't (see OutboxEventType.RETRY_PRACTICE_SUBMIT's doc).
		outbox.enqueue(
			OutboxEventType.PRACTICE_ANSWER,
			buildJsonObject {
				put("vocabularyId", row.vocabularyId)
				put("correct", correct)
				put("answeredAt", now.toString())
			},
		)

		return PracticeGradeOutcome(verdict = PracticeGrading.verdict(correct, answerKey), hearts = FULL_HEARTS)
	}

	override suspend fun complete(sessionId: String): PracticeResult {
		val session = sessionDao.getSession(sessionId)
			?: throw NoSuchElementException("Unknown local practice session: $sessionId")
		if (session.status != STATUS_COMPLETED) {
			sessionDao.upsertSession(
				session.copy(status = STATUS_COMPLETED, completedAt = System.currentTimeMillis()),
			)
		}
		return PracticeResult(
			correctCount = session.correctCount,
			totalCount = session.totalCount,
			roundsPlayed = session.roundsPlayed,
			// Matches the backend's `session.getCorrectCount() * ProgressService.XP_PER_CORRECT`
			// (`PracticeSessionService.complete()`) — it tallies already-recorded answers only, it
			// does NOT call WordProgressService, so there is no server-side mastery/review side
			// effect this local completion needs to mirror.
			xpEarned = session.correctCount * XP_PER_CORRECT,
			// No local level-up computation is ported (would need a full per-band mastered-word
			// count against the whole bundled vocabulary) — always false offline, same
			// no-progress-gamification-model choice OF3 made for lesson hearts/XP.
			levelUpEligible = false,
			stats = Stats(hearts = FULL_HEARTS, cefrLevel = session.cefrLevel),
		)
	}

	// ---- round composition (legacy picker only — §4.3/§7: no daily-planner port) -----------

	private suspend fun buildRound(sessionId: String, userId: String, level: String, round: Int, usedIds: Set<String>): PracticeRound {
		val words = legacyPick(userId, level, usedIds)
		check(words.isNotEmpty()) { "No vocabulary is available for level $level" }

		val pools = words.map { it.cefrLevel }.distinct()
			.associateWith { band -> picker.distractorPool(band, DISTRACTOR_POOL_SIZE) }
		val generated = generator.generateRound(round, words, pools)
		val rows = generated.map { it.toEntity(sessionId) }
		sessionDao.insertExercises(rows)

		return PracticeRound(
			sessionId = sessionId,
			round = round,
			cefrLevel = level,
			levelUpEligible = false,
			exercises = rows.map { it.toWireExercise() },
		)
	}

	/**
	 * Mirrors the backend's `PracticeSessionService.legacyPick` exactly: mostly the session's
	 * band, a few from earlier bands (US-12.4), topped up from all eligible bands if the
	 * band-restricted pick comes up short.
	 */
	private suspend fun legacyPick(userId: String, level: String, usedIds: Set<String>): List<LocalVocabulary> {
		val currentIndex = CEFR_LEVELS.indexOf(level).coerceAtLeast(0)
		val earlier = CEFR_LEVELS.subList(0, currentIndex)

		val earlierCount = if (earlier.isEmpty()) 0 else EARLIER_BAND_SHARE
		val words = picker.pick(userId, listOf(level), usedIds, ROUND_SIZE - earlierCount).toMutableList()
		if (earlierCount > 0) {
			words += picker.pick(userId, earlier, usedIds, earlierCount)
		}
		if (words.size < ROUND_SIZE) {
			val exclude = usedIds + words.map { it.id }
			val allBands = earlier + level
			words += picker.pick(userId, allBands, exclude, ROUND_SIZE - words.size)
		}
		return words
	}

	// ---- identity / level lookups (both zero-network) ---------------------------------------

	/**
	 * OG1 (docs/94-offline-guest-bootstrap-design.md §3.2): a [SessionState.LocalGuest] has no
	 * server user yet, so its [TokenStore.localGuestId] stands in as the userId for every local
	 * table until [com.shikhi.app.data.auth.GuestRegistrationWorker] re-keys them.
	 */
	private suspend fun currentUserId(): String = when (val state = authRepository.session.value) {
		is SessionState.Active -> state.user.id
		is SessionState.LocalGuest -> tokenStore.localGuestId()
			?: error("LocalGuest session with no stored localGuestId — invariant violated")
		else -> error("Local practice requires an already-authenticated or local-guest session")
	}

	/**
	 * The learner's last-known CEFR level, read from the same Room cache
	 * [com.shikhi.app.data.content.CachedContentRepository.stats] already populates on every
	 * successful online `stats()` call (key `"stats"`) — reused here rather than re-fetched, since
	 * this class must make zero network calls. Falls back to [Stats]'s own default ("A1") if the
	 * device has never been online, matching the DTO default a fresh account would start at.
	 */
	private suspend fun cachedCefrLevel(): String {
		val cached = cacheDao.get(STATS_CACHE_KEY) ?: return Stats().cefrLevel
		return runCatching { json.decodeFromString(Stats.serializer(), cached.json).cefrLevel }
			.getOrDefault(Stats().cefrLevel)
	}

	// ---- entity <-> generator/wire conversions -----------------------------------------------

	private fun GeneratedExercise.toEntity(sessionId: String): LocalPracticeExercise = LocalPracticeExercise(
		id = UUID.randomUUID().toString(),
		sessionId = sessionId,
		round = round,
		ordinal = ordinal,
		vocabularyId = vocabularyId,
		type = type,
		promptEn = promptEn,
		promptBn = promptBn,
		payloadJson = payload.toString(),
		answerKeyJson = answerKey.toString(),
	)

	private fun LocalPracticeExercise.toWireExercise(): PracticeExercise {
		val payload = json.parseToJsonElement(payloadJson).jsonObject
		return PracticeExercise(
			id = id,
			type = type,
			ordinal = ordinal,
			prompt = Bilingual(promptEn, promptBn),
			config = payload.toExerciseConfig(),
		)
	}

	private fun JsonObject.toExerciseConfig(): PracticeExerciseConfig = PracticeExerciseConfig(
		options = (this["options"] as? JsonArray)?.map { it.jsonObject.toChoiceOption() },
		tokens = (this["tokens"] as? JsonArray)?.map { it.jsonObject.toChoiceOption() },
		contextBn = (this["contextBn"] as? JsonPrimitive)?.contentOrNull,
		targetBn = (this["targetBn"] as? JsonPrimitive)?.contentOrNull,
		partOfSpeech = (this["partOfSpeech"] as? JsonPrimitive)?.contentOrNull,
	)

	private fun JsonObject.toChoiceOption(): ChoiceOption = ChoiceOption(
		id = getValue("id").jsonPrimitive.content,
		text = Bilingual(
			en = getValue("textEn").jsonPrimitive.content,
			bn = getValue("textBn").jsonPrimitive.content,
		),
	)

	private companion object {
		const val STATUS_IN_PROGRESS = "IN_PROGRESS"
		const val STATUS_COMPLETED = "COMPLETED"
		const val STATS_CACHE_KEY = "stats"

		// Mirrors PracticeSessionService's constants exactly.
		const val ROUND_SIZE = 10
		const val EARLIER_BAND_SHARE = 3
		const val DISTRACTOR_POOL_SIZE = 40

		/** Matches `com.shikhi.app.ui.practice.XP_PER_CORRECT` / the backend's
		 * `ProgressService.XP_PER_CORRECT`. Not shared as a single constant across the data/ui
		 * layers to avoid a data->ui dependency; value parity is enforced by
		 * `LocalPracticeSourceTest`. */
		const val XP_PER_CORRECT = 10

		/** No local hearts model is ported (§7 non-goals) — mirrors LocalLessonSource.FULL_HEARTS
		 * (the server's `UserStats.MAX_HEARTS`). */
		const val FULL_HEARTS = 5
	}
}

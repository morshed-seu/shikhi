package com.shikhi.app.data.lesson

import com.shikhi.app.data.api.dto.Bilingual
import com.shikhi.app.data.api.dto.ChoiceOption
import com.shikhi.app.data.api.dto.Exercise
import com.shikhi.app.data.api.dto.ExerciseConfig
import com.shikhi.app.data.api.dto.LessonResult
import com.shikhi.app.data.api.dto.LessonView
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.content.db.ContentAnswerKeyDao
import com.shikhi.app.data.content.db.ContentReadDao
import com.shikhi.app.data.content.db.LocalExercise
import com.shikhi.app.data.db.LocalLessonCompletion
import com.shikhi.app.data.db.LocalLessonCompletionDao
import com.shikhi.app.data.lesson.grading.LessonGradingEngine
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import com.shikhi.app.data.progress.StatsProjectionRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline lesson play (OF3, docs/93-offline-learning-design.md §3.3/§5): bundled content
 * ([ContentReadDao], OF1) + local grading ([LessonGradingEngine] against [ContentAnswerKeyDao])
 * + an outbox-buffered completion. No network calls anywhere in this class.
 *
 * UO4: hearts and lesson XP are no longer a flat constant/zero — hearts read/deplete through
 * [StatsProjectionRepository] exactly like [com.shikhi.app.data.practice.LocalPracticeSource],
 * and [complete] awards real XP gated on first completion via [lessonCompletionDao], mirroring the
 * server's `ProgressService.completeLesson` first-completion rule.
 */
@Singleton
class LocalLessonSource @Inject constructor(
	private val readDao: ContentReadDao,
	private val answerKeyDao: ContentAnswerKeyDao,
	private val outbox: OutboxRepository,
	private val statsProjectionRepository: StatsProjectionRepository,
	private val lessonCompletionDao: LocalLessonCompletionDao,
	private val authRepository: AuthRepository,
	private val tokenStore: TokenStore,
) : LessonPlaySource {

	/** sessionId -> lessonId, so [complete] can build the COMPLETE_LESSON outbox payload without
	 * the interface needing a lessonId parameter it doesn't otherwise use. Sessions are minted
	 * locally (no server round trip), so this is the only place that association is recorded. */
	private val sessionLessons = ConcurrentHashMap<String, String>()

	override suspend fun start(lessonId: String): PlayableLesson {
		val lesson = readDao.getLesson(lessonId)
			?: throw NoSuchElementException("Lesson not found in bundled content: $lessonId")
		val exercises = readDao.getExercisesForLesson(lessonId).map { toExercise(it) }
		val view = LessonView(
			id = lesson.id,
			// The bundled content has no notion of a live server content version; left blank,
			// matching LocalVocabulary/curriculum()'s treatment of server-only fields (OF2).
			contentVersion = "",
			title = Bilingual(lesson.titleEn, lesson.titleBn),
			exercises = exercises,
		)
		val sessionId = UUID.randomUUID().toString()
		sessionLessons[sessionId] = lessonId
		return PlayableLesson(
			sessionId = sessionId,
			lesson = view,
			heartsRemaining = statsProjectionRepository.currentHearts(currentUserId()),
		)
	}

	override suspend fun grade(sessionId: String, exercise: Exercise, answer: JsonObject): GradeOutcome {
		val options = answerKeyDao.getOptions(exercise.id)
		val answers = answerKeyDao.getAnswers(exercise.id)
		val hints = answerKeyDao.getHints(exercise.id)
		val verdict = LessonGradingEngine.grade(exercise.type, answer, options, answers, hints)
		val userId = currentUserId()
		// UO4: no registerActiveDay call here by design (see UO4.md's scope statement) — streak
		// only advances on practice-answer / lesson-completion, not per lesson exercise.
		if (!verdict.correct) statsProjectionRepository.loseHeart(userId)
		return GradeOutcome(verdict = verdict, heartsRemaining = statsProjectionRepository.currentHearts(userId))
	}

	/**
	 * UO4: the ledger gate mirrors the server's `ProgressService.completeLesson` first-completion
	 * XP rule — only the first COMPLETE_LESSON for a given (user, lesson, contentVersion) awards
	 * XP; repeats award 0 but still enqueue their own outbox event (bestScore-style behavior
	 * parity with the server). [eventId] is captured from [OutboxRepository.enqueue]'s return
	 * specifically so [StatsProjectionRepository.pendingXpDelta] can recognize which pending event
	 * is allowed to count — see [LocalLessonCompletion]'s doc comment for why matching purely on
	 * "does a completion row exist" isn't precise enough.
	 */
	override suspend fun complete(sessionId: String, correctCount: Int): LessonResult {
		val lessonId = sessionLessons.remove(sessionId)
			?: throw IllegalStateException("complete() called for an unknown/already-completed session: $sessionId")
		val userId = currentUserId()
		statsProjectionRepository.registerActiveDay(userId, LocalDate.now(ZoneOffset.UTC))
		// Same shape as LessonViewModel's pre-OF3 remote-failure outbox fallback — the server's
		// ProgressEventApplier COMPLETE_LESSON case needs exactly {lessonId, score} (§5).
		val eventId = outbox.enqueue(
			OutboxEventType.COMPLETE_LESSON,
			buildJsonObject {
				put("lessonId", lessonId)
				put("score", correctCount)
			},
		)
		val alreadyCompleted = lessonCompletionDao.get(userId, lessonId, CONTENT_VERSION_ID) != null
		val xpEarned = if (alreadyCompleted) 0 else correctCount * XP_PER_CORRECT
		if (!alreadyCompleted) {
			lessonCompletionDao.upsert(
				LocalLessonCompletion(
					userId = userId,
					lessonId = lessonId,
					contentVersionId = CONTENT_VERSION_ID,
					firstCompletionEventId = eventId,
					completedAt = System.currentTimeMillis(),
				),
			)
		}
		return LessonResult(
			score = correctCount,
			xpEarned = xpEarned,
			stats = Stats(hearts = statsProjectionRepository.currentHearts(userId)),
		)
	}

	/**
	 * OG1 (docs/94-offline-guest-bootstrap-design.md §3.2): same shape as
	 * [com.shikhi.app.data.practice.LocalPracticeSource]'s / [com.shikhi.app.data.progress.LevelRepository]'s
	 * identically-named private helpers — deliberately duplicated per-class in this codebase
	 * rather than shared (see those methods' doc comments).
	 */
	private suspend fun currentUserId(): String = when (val state = authRepository.session.value) {
		is SessionState.Active -> state.user.id
		is SessionState.LocalGuest -> tokenStore.localGuestId()
			?: error("LocalGuest session with no stored localGuestId — invariant violated")
		else -> error("Local lesson play requires an already-authenticated or local-guest session")
	}

	private suspend fun toExercise(local: LocalExercise): Exercise {
		// Mirrors the backend's CurriculumQueryService.toExerciseView/optionViews exactly: MCQ
		// (and MATCH, though ungraded) render `options`, WORD_BANK renders `tokens` — both drawn
		// from the same exercise_options table, stripped of `isCorrect` before it ever reaches
		// this DTO, which is what LessonViewModel/Composables actually see.
		val config = when (local.type) {
			"MCQ", "MATCH" -> ExerciseConfig(options = renderOptions(local.id))
			"WORD_BANK" -> ExerciseConfig(tokens = renderOptions(local.id))
			else -> ExerciseConfig()
		}
		return Exercise(
			id = local.id,
			type = local.type,
			ordinal = local.ordinal,
			prompt = Bilingual(local.promptEn, local.promptBn),
			mediaRef = local.mediaRef,
			config = config,
		)
	}

	private suspend fun renderOptions(exerciseId: String): List<ChoiceOption> =
		answerKeyDao.getOptions(exerciseId).map { ChoiceOption(id = it.id, text = Bilingual(it.textEn, it.textBn)) }

	private companion object {
		/** Matches `LocalPracticeSource.XP_PER_CORRECT` / `com.shikhi.app.ui.practice.XP_PER_CORRECT`
		 * / the backend's `ProgressService.XP_PER_CORRECT` — duplicated per this codebase's
		 * convention (see LocalPracticeSource's own copy for the reasoning). */
		const val XP_PER_CORRECT = 10

		/** Bundled content has no live contentVersion — matches `LessonView.contentVersion`'s
		 * existing blank convention (OF2). */
		const val CONTENT_VERSION_ID = ""
	}
}

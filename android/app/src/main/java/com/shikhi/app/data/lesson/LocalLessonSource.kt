package com.shikhi.app.data.lesson

import com.shikhi.app.data.api.dto.Bilingual
import com.shikhi.app.data.api.dto.ChoiceOption
import com.shikhi.app.data.api.dto.Exercise
import com.shikhi.app.data.api.dto.ExerciseConfig
import com.shikhi.app.data.api.dto.LessonResult
import com.shikhi.app.data.api.dto.LessonView
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.content.db.ContentAnswerKeyDao
import com.shikhi.app.data.content.db.ContentReadDao
import com.shikhi.app.data.content.db.LocalExercise
import com.shikhi.app.data.lesson.grading.LessonGradingEngine
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline lesson play (OF3, docs/93-offline-learning-design.md §3.3/§5): bundled content
 * ([ContentReadDao], OF1) + local grading ([LessonGradingEngine] against [ContentAnswerKeyDao])
 * + an outbox-buffered completion. No network calls anywhere in this class.
 *
 * There is no local hearts/XP model to port (§7 non-goals — only grading is ported, not the
 * progress/gamification module), so hearts are reported as a constant full count
 * ([FULL_HEARTS], mirroring the server's `UserStats.MAX_HEARTS`) for the whole session rather
 * than depleted on a wrong answer.
 */
@Singleton
class LocalLessonSource @Inject constructor(
	private val readDao: ContentReadDao,
	private val answerKeyDao: ContentAnswerKeyDao,
	private val outbox: OutboxRepository,
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
		return PlayableLesson(sessionId = sessionId, lesson = view, heartsRemaining = FULL_HEARTS)
	}

	override suspend fun grade(sessionId: String, exercise: Exercise, answer: JsonObject): GradeOutcome {
		val options = answerKeyDao.getOptions(exercise.id)
		val answers = answerKeyDao.getAnswers(exercise.id)
		val hints = answerKeyDao.getHints(exercise.id)
		val verdict = LessonGradingEngine.grade(exercise.type, answer, options, answers, hints)
		return GradeOutcome(verdict = verdict, heartsRemaining = FULL_HEARTS)
	}

	override suspend fun complete(sessionId: String, correctCount: Int): LessonResult {
		val lessonId = sessionLessons.remove(sessionId)
			?: throw IllegalStateException("complete() called for an unknown/already-completed session: $sessionId")
		// Same shape as LessonViewModel's pre-OF3 remote-failure outbox fallback — the server's
		// ProgressEventApplier COMPLETE_LESSON case needs exactly {lessonId, score} (§5).
		outbox.enqueue(
			OutboxEventType.COMPLETE_LESSON,
			buildJsonObject {
				put("lessonId", lessonId)
				put("score", correctCount)
			},
		)
		return LessonResult(score = correctCount, xpEarned = 0, stats = Stats(hearts = FULL_HEARTS))
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
		/** Mirrors the server's `UserStats.MAX_HEARTS` — no hearts depletion is ported (§7). */
		const val FULL_HEARTS = 5
	}
}

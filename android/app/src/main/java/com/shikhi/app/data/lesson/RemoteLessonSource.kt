package com.shikhi.app.data.lesson

import com.shikhi.app.data.api.ContentApi
import com.shikhi.app.data.api.LearningApi
import com.shikhi.app.data.api.dto.Exercise
import com.shikhi.app.data.api.dto.IdempotentRequest
import com.shikhi.app.data.api.dto.LessonResult
import com.shikhi.app.data.api.dto.StartSessionRequest
import com.shikhi.app.data.api.dto.SubmitAnswerRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online lesson play: a thin, behavior-preserving wrapper around today's `ContentApi`/
 * `LearningApi` calls (docs/93-offline-learning-design.md §3.3) — this is exactly what
 * `LessonViewModel` called directly before OF3, so online users see no change at all.
 */
@Singleton
class RemoteLessonSource @Inject constructor(
	private val contentApi: ContentApi,
	private val learningApi: LearningApi,
) : LessonPlaySource {

	override suspend fun start(lessonId: String): PlayableLesson = coroutineScope {
		val lesson = async { contentApi.lesson(lessonId) }
		val session = async { learningApi.startSession(StartSessionRequest(lessonId)) }
		val started = session.await()
		PlayableLesson(
			sessionId = started.id,
			lesson = lesson.await(),
			heartsRemaining = started.heartsRemaining,
		)
	}

	override suspend fun grade(sessionId: String, exercise: Exercise, answer: JsonObject): GradeOutcome {
		val result = learningApi.submitAnswer(
			sessionId,
			SubmitAnswerRequest(
				idempotencyKey = UUID.randomUUID().toString(),
				exerciseId = exercise.id,
				answer = answer,
			),
		)
		return GradeOutcome(verdict = result.verdict, heartsRemaining = result.stats.hearts)
	}

	override suspend fun complete(sessionId: String, correctCount: Int): LessonResult =
		// The server already tracked the score via each submitAnswer call; correctCount is the
		// local mirror of that same tally and isn't sent — completeSession takes no body fields
		// beyond the idempotency key, matching the pre-OF3 call site exactly.
		learningApi.completeSession(sessionId, IdempotentRequest(UUID.randomUUID().toString()))
}

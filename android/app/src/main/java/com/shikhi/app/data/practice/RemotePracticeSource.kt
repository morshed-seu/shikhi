package com.shikhi.app.data.practice

import com.shikhi.app.data.api.PracticeApi
import com.shikhi.app.data.api.dto.IdempotentRequest
import com.shikhi.app.data.api.dto.PracticeExercise
import com.shikhi.app.data.api.dto.PracticeResult
import com.shikhi.app.data.api.dto.PracticeRound
import com.shikhi.app.data.api.dto.SubmitAnswerRequest
import com.shikhi.app.data.api.dto.Verdict
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online practice play: a thin wrapper around today's `PracticeApi` calls
 * (docs/93-offline-learning-design.md §3.3) — this is what `PracticeViewModel` called directly
 * before OF4, so online users see no change to the happy path.
 *
 * Before OF4, a failed `submitAnswer` call silently fabricated a "wrong" verdict and the real
 * answer was lost forever — the server never saw it, so mastery/review state for that word never
 * updated (worse than a lesson-completion loss, since practice's progress-bearing side effects
 * happen per-answer, not just at session end; see design doc §5). [grade] now buffers a
 * recoverable failure (network/5xx — a genuine "offline right now" or "server hiccup," not a
 * validation error that would fail again identically) as an
 * [OutboxEventType.RETRY_PRACTICE_SUBMIT] event and lets [OutboxRepository.flush] replay the
 * *exact original request* (same idempotency key) once connectivity returns. The verdict shown to
 * the learner in the meantime is still an optimistic "not quite" (the true verdict genuinely isn't
 * knowable client-side — grading is server-only for a remote session) — the fix is that the answer
 * itself is no longer lost, not that the immediate UI becomes clairvoyant.
 */
@Singleton
class RemotePracticeSource @Inject constructor(
	private val practiceApi: PracticeApi,
	private val outbox: OutboxRepository,
) : PracticePlaySource {

	override suspend fun start(): PracticeRound = practiceApi.start()

	override suspend fun grade(sessionId: String, exercise: PracticeExercise, answer: JsonObject): PracticeGradeOutcome {
		val idempotencyKey = UUID.randomUUID().toString()
		return try {
			val result = practiceApi.submitAnswer(sessionId, SubmitAnswerRequest(idempotencyKey, exercise.id, answer))
			PracticeGradeOutcome(verdict = result.verdict, hearts = result.stats.hearts)
		} catch (e: Exception) {
			if (recoverable(e)) {
				outbox.enqueue(
					OutboxEventType.RETRY_PRACTICE_SUBMIT,
					buildJsonObject {
						put("sessionId", sessionId)
						put("exerciseId", exercise.id)
						put("idempotencyKey", idempotencyKey)
						put("answer", answer)
					},
				)
			}
			PracticeGradeOutcome(verdict = Verdict(correct = false), hearts = null)
		}
	}

	override suspend fun nextRound(sessionId: String): PracticeRound = practiceApi.nextRound(sessionId)

	override suspend fun complete(sessionId: String): PracticeResult =
		practiceApi.complete(sessionId, IdempotentRequest(UUID.randomUUID().toString()))

	private fun recoverable(e: Exception) = e is IOException || (e is HttpException && e.code() >= 500)
}

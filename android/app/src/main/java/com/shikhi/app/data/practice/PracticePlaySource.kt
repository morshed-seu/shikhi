package com.shikhi.app.data.practice

import com.shikhi.app.data.api.dto.PracticeExercise
import com.shikhi.app.data.api.dto.PracticeResult
import com.shikhi.app.data.api.dto.PracticeRound
import com.shikhi.app.data.api.dto.Verdict
import kotlinx.serialization.json.JsonObject

/**
 * The result of grading one practice answer plus the learner's hearts after it. `hearts` is
 * `null` when the source has no fresh figure to report (e.g. [RemotePracticeSource] buffered a
 * failed submit for later retry) — callers should keep showing whatever hearts count they already
 * had rather than treat `null` as zero.
 */
data class PracticeGradeOutcome(
	val verdict: Verdict,
	val hearts: Int? = null,
)

/**
 * Where a practice session's round generation, grading, and completion come from — the server
 * (online) or bundled vocabulary + the local practice engine (offline), per
 * docs/93-offline-learning-design.md §3.3. [com.shikhi.app.ui.practice.PracticeViewModel] resolves
 * which implementation to use **once**, at session start (see
 * [com.shikhi.app.data.connectivity.ConnectivityChecker]) — never mid-session, mirroring OF3's
 * [com.shikhi.app.data.lesson.LessonPlaySource] exactly.
 *
 * The wire [PracticeRound] DTO already carries `sessionId`, so unlike
 * [com.shikhi.app.data.lesson.PlayableLesson] this interface needs no separate wrapper for it —
 * [start]/[nextRound] just return [PracticeRound] directly.
 *
 * [RemotePracticeSource] wraps today's `PracticeApi`/`ProgressApi` calls — online users keep
 * exactly today's behavior, plus new outbox-buffered resilience on a failed [grade] (see that
 * class's doc). [LocalPracticeSource] is the new OF4 offline path.
 */
interface PracticePlaySource {

	suspend fun start(): PracticeRound

	suspend fun grade(sessionId: String, exercise: PracticeExercise, answer: JsonObject): PracticeGradeOutcome

	suspend fun nextRound(sessionId: String): PracticeRound

	suspend fun complete(sessionId: String): PracticeResult
}

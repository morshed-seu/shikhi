package com.shikhi.app.data.lesson

import com.shikhi.app.data.api.dto.Exercise
import com.shikhi.app.data.api.dto.LessonResult
import com.shikhi.app.data.api.dto.LessonView
import com.shikhi.app.data.api.dto.Verdict
import kotlinx.serialization.json.JsonObject

/**
 * A playable lesson plus the session id [LessonPlaySource.grade]/[LessonPlaySource.complete]
 * need. `heartsRemaining` mirrors `LessonSession.heartsRemaining` for the remote path; the
 * local path has no hearts/XP model to port (§7 non-goals) so it reports a constant full count
 * — see [LocalLessonSource].
 */
data class PlayableLesson(
	val sessionId: String,
	val lesson: LessonView,
	val heartsRemaining: Int,
)

/** The result of grading one answer plus the learner's hearts after it. */
data class GradeOutcome(
	val verdict: Verdict,
	val heartsRemaining: Int,
)

/**
 * Where a lesson session's content, grading, and completion come from — the server (online) or
 * bundled content + the local grading engine (offline), per
 * docs/93-offline-learning-design.md §3.3. [LessonViewModel][com.shikhi.app.ui.lesson.LessonViewModel]
 * resolves which implementation to use **once**, at session start (see
 * [com.shikhi.app.data.connectivity.ConnectivityChecker]) — never mid-session, so a session's
 * grading source can't switch mid-answer.
 *
 * [RemoteLessonSource] wraps today's `ContentApi`/`LearningApi` calls unchanged — online users
 * keep exactly today's behavior. [LocalLessonSource] is the new OF3 offline path.
 */
interface LessonPlaySource {

	suspend fun start(lessonId: String): PlayableLesson

	suspend fun grade(sessionId: String, exercise: Exercise, answer: JsonObject): GradeOutcome

	suspend fun complete(sessionId: String, correctCount: Int): LessonResult
}

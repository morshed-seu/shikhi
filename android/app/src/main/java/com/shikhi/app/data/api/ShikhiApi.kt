package com.shikhi.app.data.api

import com.shikhi.app.data.api.dto.AnswerResult
import com.shikhi.app.data.api.dto.CurriculumTree
import com.shikhi.app.data.api.dto.GuestRequest
import com.shikhi.app.data.api.dto.IdempotentRequest
import com.shikhi.app.data.api.dto.LessonResult
import com.shikhi.app.data.api.dto.LessonSession
import com.shikhi.app.data.api.dto.LessonView
import com.shikhi.app.data.api.dto.RefreshRequest
import com.shikhi.app.data.api.dto.StartSessionRequest
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.SubmitAnswerRequest
import com.shikhi.app.data.api.dto.SyncBatchRequest
import com.shikhi.app.data.api.dto.TokenPair
import com.shikhi.app.data.api.dto.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Paths are relative so they resolve under the /v1 base URL (BuildConfig.API_BASE_URL).

/** Endpoints that must not carry a bearer token — served by the plain OkHttp client. */
interface AuthApi {
	@POST("auth/guest")
	suspend fun guest(@Body body: GuestRequest): TokenPair

	@POST("auth/refresh")
	suspend fun refresh(@Body body: RefreshRequest): TokenPair

	@GET("health")
	suspend fun health(): Response<Unit>
}

/** Authenticated endpoints — served by the OkHttp client with interceptor + authenticator. */
interface UserApi {
	@GET("me")
	suspend fun me(): User

	@POST("auth/logout")
	suspend fun logout(): Response<Unit>
}

/** Published curriculum + lessons (content module, read-only). */
interface ContentApi {
	@GET("curriculum")
	suspend fun curriculum(): CurriculumTree

	@GET("lessons/{lessonId}")
	suspend fun lesson(@Path("lessonId") lessonId: String): LessonView
}

/** Lesson session play-through (learning module). */
interface LearningApi {
	@POST("sessions")
	suspend fun startSession(@Body body: StartSessionRequest): LessonSession

	@POST("sessions/{sessionId}/answers")
	suspend fun submitAnswer(
		@Path("sessionId") sessionId: String,
		@Body body: SubmitAnswerRequest,
	): AnswerResult

	@POST("sessions/{sessionId}/complete")
	suspend fun completeSession(
		@Path("sessionId") sessionId: String,
		@Body body: IdempotentRequest,
	): LessonResult
}

/** Stats + idempotent batch reconciliation of buffered progress events (D2/NFR-N2). */
interface ProgressApi {
	@GET("stats")
	suspend fun stats(): Stats

	@POST("progress/sync")
	suspend fun sync(@Body body: SyncBatchRequest): Stats
}

package com.shikhi.app.data.api

import com.shikhi.app.data.api.dto.AnswerResult
import com.shikhi.app.data.api.dto.ClaimRequest
import com.shikhi.app.data.api.dto.CurriculumTree
import com.shikhi.app.data.api.dto.GuestRequest
import com.shikhi.app.data.api.dto.IdempotentRequest
import com.shikhi.app.data.api.dto.LessonResult
import com.shikhi.app.data.api.dto.LessonSession
import com.shikhi.app.data.api.dto.LessonView
import com.shikhi.app.data.api.dto.LoginRequest
import com.shikhi.app.data.api.dto.PracticeResult
import com.shikhi.app.data.api.dto.PracticeRound
import com.shikhi.app.data.api.dto.RefreshRequest
import com.shikhi.app.data.api.dto.RegisterRequest
import com.shikhi.app.data.api.dto.ReviewItem
import com.shikhi.app.data.api.dto.ReviewResultsRequest
import com.shikhi.app.data.api.dto.SetLevelRequest
import com.shikhi.app.data.api.dto.StartSessionRequest
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.SubmitAnswerRequest
import com.shikhi.app.data.api.dto.SyncBatchRequest
import com.shikhi.app.data.api.dto.TokenPair
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.api.dto.VocabularyEntry
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Paths are relative so they resolve under the /v1 base URL (BuildConfig.API_BASE_URL).

/** Endpoints that must not carry a bearer token — served by the plain OkHttp client. */
interface AuthApi {
	@POST("auth/guest")
	suspend fun guest(@Body body: GuestRequest): TokenPair

	@POST("auth/refresh")
	suspend fun refresh(@Body body: RefreshRequest): TokenPair

	@POST("auth/register")
	suspend fun register(@Body body: RegisterRequest): TokenPair

	@POST("auth/login")
	suspend fun login(@Body body: LoginRequest): TokenPair

	@GET("health")
	suspend fun health(): Response<Unit>
}

/** Authenticated endpoints — served by the OkHttp client with interceptor + authenticator. */
interface UserApi {
	@GET("me")
	suspend fun me(): User

	@POST("auth/logout")
	suspend fun logout(): Response<Unit>

	/** In-place guest upgrade (ADR-0011); authenticated as the guest, returns rotated tokens. */
	@POST("auth/claim")
	suspend fun claim(@Body body: ClaimRequest): TokenPair
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

	/** Self-placement or an accepted level-up (E12). */
	@PUT("stats/level")
	suspend fun setLevel(@Body body: SetLevelRequest): Stats
}

/** Adaptive vocabulary practice sessions (E12) — exercises generated server-side. */
interface PracticeApi {
	@POST("practice/sessions")
	suspend fun start(): PracticeRound

	@POST("practice/sessions/{sessionId}/answers")
	suspend fun submitAnswer(
		@Path("sessionId") sessionId: String,
		@Body body: SubmitAnswerRequest,
	): AnswerResult

	@POST("practice/sessions/{sessionId}/rounds")
	suspend fun nextRound(@Path("sessionId") sessionId: String): PracticeRound

	@POST("practice/sessions/{sessionId}/complete")
	suspend fun complete(
		@Path("sessionId") sessionId: String,
		@Body body: IdempotentRequest,
	): PracticeResult
}

/** Leitner spaced-repetition review — self-graded recall (M6). */
interface ReviewApi {
	@GET("review/due")
	suspend fun due(): List<ReviewItem>

	@POST("review/results")
	suspend fun results(@Body body: ReviewResultsRequest): Response<Unit>
}

/** Oxford-5000 dictionary browser, one CEFR band at a time. */
interface VocabularyApi {
	@GET("vocabulary")
	suspend fun list(@Query("level") level: String): List<VocabularyEntry>
}

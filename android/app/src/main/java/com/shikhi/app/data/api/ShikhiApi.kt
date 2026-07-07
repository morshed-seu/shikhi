package com.shikhi.app.data.api

import com.shikhi.app.data.api.dto.GuestRequest
import com.shikhi.app.data.api.dto.RefreshRequest
import com.shikhi.app.data.api.dto.TokenPair
import com.shikhi.app.data.api.dto.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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

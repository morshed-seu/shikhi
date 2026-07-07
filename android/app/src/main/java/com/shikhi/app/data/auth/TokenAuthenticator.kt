package com.shikhi.app.data.auth

import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refresh-and-retry on 401, mirroring the web client's semantics with two hard rules
 * dictated by the backend's rotating refresh tokens (family revoked on replay — see
 * ADR-0012 and identity/service/RefreshTokenService):
 *
 * 1. Refreshes are single-flighted behind a mutex: concurrent 401s trigger ONE
 *    `/auth/refresh`; the losers reuse the winner's new access token.
 * 2. The rotated refresh token is PERSISTED before the retried request is returned —
 *    a crash between refresh and persist must never leave a stale token on disk.
 *
 * A failed refresh with a 4xx means the session is dead: the store is cleared, which
 * the session layer observes and lands the user on onboarding.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
	private val tokenStore: TokenStore,
	private val authApi: dagger.Lazy<AuthApi>,
) : Authenticator {

	private val mutex = Mutex()

	override fun authenticate(route: Route?, response: Response): Request? {
		// Only retry requests that actually carried a bearer token, exactly once.
		val failedToken = response.request.header("Authorization")
			?.removePrefix("Bearer ") ?: return null
		if (response.priorResponse != null) return null

		return runBlocking {
			mutex.withLock {
				// Someone else may have refreshed while we waited on the lock.
				val current = tokenStore.accessToken.value
				if (current != null && current != failedToken) {
					return@withLock response.request.withBearer(current)
				}

				val refreshToken = tokenStore.currentRefreshToken()
					?: return@withLock null

				val pair = try {
					authApi.get().refresh(RefreshRequest(refreshToken))
				} catch (e: HttpException) {
					if (e.code() in 400..499) tokenStore.clear()
					return@withLock null
				} catch (e: IOException) {
					return@withLock null
				}

				// Persist the rotation BEFORE retrying (rule 2).
				tokenStore.setSession(pair.accessToken, pair.refreshToken)
				response.request.withBearer(pair.accessToken)
			}
		}
	}

	private fun Request.withBearer(token: String): Request =
		newBuilder().header("Authorization", "Bearer $token").build()
}

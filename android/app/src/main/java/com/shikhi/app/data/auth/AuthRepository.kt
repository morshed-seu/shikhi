package com.shikhi.app.data.auth

import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.api.dto.ClaimRequest
import com.shikhi.app.data.api.dto.GuestRequest
import com.shikhi.app.data.api.dto.LoginRequest
import com.shikhi.app.data.api.dto.RefreshRequest
import com.shikhi.app.data.api.dto.RegisterRequest
import com.shikhi.app.data.api.dto.TokenPair
import com.shikhi.app.data.api.dto.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
	/** Startup: silent sign-in via the stored refresh token is in flight. */
	data object Loading : SessionState

	data object LoggedOut : SessionState

	data class Active(val user: User) : SessionState

	/** Bootstrap failed for a non-auth reason (network/server) — the stored session is kept. */
	data object Unavailable : SessionState
}

/**
 * Session lifecycle, mirroring frontend/src/auth/AuthProvider.tsx: bootstrap on launch
 * (refresh → /me), guest-first start (ADR-0011), logout. If the TokenAuthenticator kills
 * the session mid-flight (dead refresh token), the cleared store is observed here and the
 * UI falls back to onboarding.
 */
@Singleton
class AuthRepository @Inject constructor(
	private val authApi: AuthApi,
	private val userApi: dagger.Lazy<UserApi>,
	private val tokenStore: TokenStore,
	appScope: CoroutineScope,
) {

	private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
	val session: StateFlow<SessionState> = _session

	init {
		appScope.launch {
			tokenStore.accessToken.collect { token ->
				if (token == null && _session.value is SessionState.Active) {
					_session.value = SessionState.LoggedOut
				}
				// Reconnect after an offline launch: the Authenticator refreshed on the
				// first 401, proving the stored session is alive — finish the bootstrap.
				if (token != null && _session.value is SessionState.Unavailable) {
					runCatching { userApi.get().me() }.onSuccess { user ->
						_session.value = SessionState.Active(user)
					}
				}
			}
		}
	}

	suspend fun bootstrap() {
		val refreshToken = tokenStore.currentRefreshToken()
		if (refreshToken == null) {
			_session.value = SessionState.LoggedOut
			return
		}
		try {
			val pair = authApi.refresh(RefreshRequest(refreshToken))
			tokenStore.setSession(pair.accessToken, pair.refreshToken)
			_session.value = SessionState.Active(userApi.get().me())
		} catch (e: HttpException) {
			if (e.code() in 400..499) {
				// Session is genuinely dead (revoked/expired) — forget it.
				tokenStore.clear()
				_session.value = SessionState.LoggedOut
			} else {
				_session.value = SessionState.Unavailable
			}
		} catch (e: IOException) {
			// Offline/cold-starting backend: keep the stored token, let the user retry.
			_session.value = SessionState.Unavailable
		}
	}

	suspend fun startGuest(uiLocale: String) {
		activate(authApi.guest(GuestRequest(uiLocale)))
	}

	suspend fun login(email: String, password: String) {
		activate(authApi.login(LoginRequest(email, password)))
	}

	suspend fun register(email: String, password: String, displayName: String?, uiLocale: String) {
		activate(authApi.register(RegisterRequest(email, password, displayName, uiLocale)))
	}

	/**
	 * Upgrade the current guest in place (ADR-0011): adds email+password to the same user
	 * id — all progress carries over. Returns rotated tokens; the caller stays signed in.
	 */
	suspend fun claim(email: String, password: String, displayName: String?) {
		activate(userApi.get().claim(ClaimRequest(email, password, displayName)))
	}

	private suspend fun activate(pair: TokenPair) {
		tokenStore.setSession(pair.accessToken, pair.refreshToken)
		_session.value = SessionState.Active(userApi.get().me())
	}

	suspend fun logout() {
		try {
			userApi.get().logout()
		} catch (_: Exception) {
			// Best-effort server-side revocation; local logout always succeeds.
		} finally {
			tokenStore.clear()
			_session.value = SessionState.LoggedOut
		}
	}
}

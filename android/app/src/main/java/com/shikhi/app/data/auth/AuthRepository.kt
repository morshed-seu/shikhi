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
import com.shikhi.app.data.connectivity.ConnectivityChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
	/** Startup: silent sign-in via the stored refresh token is in flight. */
	data object Loading : SessionState

	// GF1: no longer set as a terminal/user-facing state from bootstrap()/init{}/logout() —
	// those paths all silently re-provision a guest now (see provisionGuestOrFail) and only
	// fall back to GuestUnavailable if that itself fails. Kept for exhaustiveness/back-compat.
	data object LoggedOut : SessionState

	data class Active(val user: User) : SessionState

	/** Bootstrap failed for a non-auth reason (network/server) — the stored session is kept. */
	data object Unavailable : SessionState

	/** No stored session, and silently becoming a guest failed (offline or backend unreachable). */
	data object GuestUnavailable : SessionState
}

/**
 * Session lifecycle, mirroring frontend/src/auth/AuthProvider.tsx: bootstrap on launch
 * (refresh → /me), guest-first start (ADR-0011), logout. If the TokenAuthenticator kills
 * the session mid-flight (dead refresh token), the cleared store is observed here and a
 * fresh guest session is silently re-provisioned (GF1) — no login wall is ever shown unless
 * that guest provisioning itself fails, in which case [SessionState.GuestUnavailable] surfaces
 * a minimal "Connecting…" retry screen.
 */
@Singleton
class AuthRepository @Inject constructor(
	private val authApi: AuthApi,
	private val userApi: dagger.Lazy<UserApi>,
	private val tokenStore: TokenStore,
	private val connectivity: ConnectivityChecker,
	appScope: CoroutineScope,
) {

	private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
	val session: StateFlow<SessionState> = _session

	init {
		appScope.launch {
			tokenStore.accessToken.collect { token ->
				if (token == null && _session.value is SessionState.Active) {
					// GF1: a killed session (dead/revoked refresh token via the
					// Authenticator) silently re-guests rather than showing a login wall.
					provisionGuestOrFail(uiLocale())
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
			provisionGuestOrFail(uiLocale())
			return
		}
		try {
			val pair = authApi.refresh(RefreshRequest(refreshToken))
			tokenStore.setSession(pair.accessToken, pair.refreshToken)
			_session.value = SessionState.Active(userApi.get().me())
		} catch (e: HttpException) {
			if (e.code() in 400..499) {
				// Session is genuinely dead (revoked/expired) — forget it and re-guest.
				tokenStore.clear()
				provisionGuestOrFail(uiLocale())
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

	/**
	 * GF1: the replacement for the old blocking "login wall" — silently creates a guest
	 * session so the app opens straight to Home. Fast-fails to [SessionState.GuestUnavailable]
	 * when offline (no point trying the network call); otherwise retries [startGuest] with the
	 * same bounded backoff as [com.shikhi.app.ui.home.HomeViewModel]'s health check before
	 * giving up. [startGuest] sets [SessionState.Active] itself via [activate] on success.
	 */
	private suspend fun provisionGuestOrFail(uiLocale: String) {
		if (!connectivity.isOnline()) {
			_session.value = SessionState.GuestUnavailable
			return
		}
		val delays = listOf(0L, 5_000L, 10_000L, 15_000L, 20_000L)
		for (wait in delays) {
			delay(wait)
			try {
				startGuest(uiLocale)
				return
			} catch (_: IOException) {
				// fall through to retry/backoff
			} catch (_: HttpException) {
				// fall through to retry/backoff
			}
		}
		_session.value = SessionState.GuestUnavailable
	}

	/** Manual retry from the "Connecting…" screen after [provisionGuestOrFail] gave up. */
	suspend fun retryGuestProvisioning() {
		provisionGuestOrFail(uiLocale())
	}

	private fun uiLocale() = if (Locale.getDefault().language == "en") "en" else "bn"

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

	/**
	 * Replaces the user inside the current Active session after a profile edit (E13/US-13.2:
	 * the new name/locale must show everywhere, not just on the screen that PATCHed it —
	 * ProfileViewModel is back-stack-entry-scoped, so a revisit re-seeds from this session).
	 * Tokens are untouched; a no-op unless a session is Active.
	 */
	fun updateActiveUser(user: User) {
		_session.update { current -> if (current is SessionState.Active) SessionState.Active(user) else current }
	}

	suspend fun logout() {
		try {
			userApi.get().logout()
		} catch (_: Exception) {
			// Best-effort server-side revocation; local logout always succeeds.
		} finally {
			tokenStore.clear()
			// GF1: back to guest-mode Home immediately, not a login wall (requirement 5).
			provisionGuestOrFail(uiLocale())
		}
	}
}

package com.shikhi.app.data.auth

import androidx.work.WorkManager
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
import java.util.UUID
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

	/**
	 * OG1 (docs/94-offline-guest-bootstrap-design.md §3.2): zero-network guest — a client-only
	 * [TokenStore.localGuestId] is already usable as the userId for every local table, so the app
	 * is fully usable immediately. [GuestRegistrationWorker] registers the real server account in
	 * the background; there is no learner-visible difference from [Active].
	 */
	data object LocalGuest : SessionState

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
	private val workManager: dagger.Lazy<WorkManager>,
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
				// OG2 finding-1 fix: LocalGuest -> Active is NOT reacted to generically here
				// anymore. GuestRegistrationWorker.doWork() calls tokenStore.setSession(...)
				// *before* its own re-key transaction runs, so a generic "token appeared" collector
				// racing that transaction could flip the session to Active while local Room rows
				// are still keyed under the old localGuestId — see ADR-0014 finding 1. The worker
				// is now the sole authority for this transition; it calls
				// completeGuestRegistration() itself once the re-key + clearLocalGuestId() have
				// both completed.
			}
		}
	}

	suspend fun bootstrap() {
		val refreshToken = tokenStore.currentRefreshToken()
		if (refreshToken == null) {
			startFreshGuestSession()
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
		// ADR-0014 finding-2 fix: a process death between GuestRegistrationWorker's setSession
		// and its re-key transaction/clearLocalGuestId() would otherwise abandon the local rows
		// under localGuestId forever — the ordinary refresh-token/Active branch above never looks
		// at localGuestId again. Defensively re-enqueue (idempotent: KEEP) any time one is still
		// on disk, regardless of which branch above just ran, as a no-cost safety net.
		if (tokenStore.localGuestId() != null) enqueueGuestRegistration()
	}

	/**
	 * The "no session at all" decision (OG1 design doc §3.2): resume a previously-minted
	 * [SessionState.LocalGuest] with no network attempt, fast-path straight to a real guest
	 * session if online, or mint a fresh local-only guest id if offline. Shared by [bootstrap]'s
	 * no-refresh-token branch and [logout]'s final step (ADR-0014 finding-3 fix) — both are the
	 * same situation: no session, decide LocalGuest vs. immediate Active.
	 */
	private suspend fun startFreshGuestSession() {
		val storedLocalGuestId = tokenStore.localGuestId()
		when {
			// A previous local-only launch already minted an id but never reached the
			// network — resume as LocalGuest immediately, no network attempt.
			storedLocalGuestId != null -> enterLocalGuest()
			// Online: today's fast path (network guest provisioning) still wins — no need for
			// the local phase if we can just succeed immediately (GF5 test coverage for this
			// path is preserved as-is).
			connectivity.isOnline() -> provisionGuestOrFail(uiLocale())
			// True cold start, offline: mint a local-only guest id and register later.
			else -> {
				tokenStore.setLocalGuestId(UUID.randomUUID().toString())
				enterLocalGuest()
			}
		}
	}

	suspend fun startGuest(uiLocale: String) {
		activate(authApi.guest(GuestRequest(uiLocale)))
	}

	/**
	 * OG1/OG2: enters [SessionState.LocalGuest] (a [TokenStore.localGuestId] must already be
	 * persisted by the caller) and kicks off [GuestRegistrationWorker] in the background —
	 * unique work, so this is safe to call again on every process start while still LocalGuest
	 * without double-registering (design doc §7 risk 2).
	 */
	private fun enterLocalGuest() {
		_session.value = SessionState.LocalGuest
		enqueueGuestRegistration()
	}

	/** Small WorkManager-boilerplate wrapper — called from more than one place (finding 2). */
	private fun enqueueGuestRegistration() {
		GuestRegistrationWorker.schedule(workManager.get())
	}

	/**
	 * ADR-0014 finding-1 fix: the ONLY place [SessionState.LocalGuest] transitions to
	 * [SessionState.Active] — called by [GuestRegistrationWorker] once its re-key transaction and
	 * `clearLocalGuestId()` have both completed successfully, never reacted to generically from a
	 * token appearing (that raced the worker's own rekey).
	 */
	suspend fun completeGuestRegistration(user: User) {
		_session.value = SessionState.Active(user)
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
		// ADR-0014 finding-4 fix: login()/claim()/register()/startGuest() all funnel through here,
		// and any of them can be reached from GuestBanner's "sign in to existing account" flow
		// while the session is still LocalGuest (ADR-0011/GF3: that flow is an explicit, lossy,
		// non-merging discard of the local guest's progress). Without this guard, the pending
		// GuestRegistrationWorker can still land later and silently re-key the discarded local
		// guest's rows onto whatever account just got activated here — the same class of bug
		// logout()'s finding-3 fix closed, reached through a different door.
		//
		// This must run BEFORE tokenStore.setSession(pair...) below, not merely before
		// _session.value = Active(...): GuestRegistrationWorker.doWork() decides whether to call
		// authApi.guest() at all based on tokenStore.currentRefreshToken() == null. If this guard
		// ran after setSession, a worker that starts between the two calls would read the OLD
		// localGuestId (not yet cleared) together with the NEW refresh token (already set),
		// conclude it's a retry of its own prior attempt, skip authApi.guest() entirely, call
		// /me as the newly-activated account, and rekey the discarded guest's rows onto it —
		// exactly the corruption this fix exists to prevent. Running the guard first closes that
		// window for any worker execution that has not yet read localGuestId() by the time this
		// runs.
		//
		// Residual TOCTOU: an execution of doWork() that is already PAST its localGuestId() read
		// at the exact instant this guard runs cannot be stopped here — cancelUniqueWork() only
		// prevents future/enqueued runs, it does not interrupt code already executing. That
		// narrow window is closed instead by a re-check inside doWork() itself, immediately
		// before the rekey transaction, comparing the freshly re-read localGuestId() against the
		// value it captured at the start (see GuestRegistrationWorker.doWork()).
		if (_session.value is SessionState.LocalGuest) {
			workManager.get().cancelUniqueWork(GuestRegistrationWorker.UNIQUE_NAME)
			tokenStore.clearLocalGuestId()
		}
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
		// ADR-0014 finding-3 fix: a LocalGuest logging out must cancel its own pending
		// registration — otherwise it can fire later (once connectivity returns) and silently
		// setSession(...) over whatever session is current by then.
		if (_session.value is SessionState.LocalGuest) {
			workManager.get().cancelUniqueWork(GuestRegistrationWorker.UNIQUE_NAME)
		}
		try {
			userApi.get().logout()
		} catch (_: Exception) {
			// Best-effort server-side revocation; local logout always succeeds.
		} finally {
			tokenStore.clear()
			// The old localGuestId (if any) is being abandoned intentionally — nothing should
			// ever try to re-key its now-orphaned local rows again.
			tokenStore.clearLocalGuestId()
			// GF1: back to guest-mode Home immediately, not a login wall (requirement 5). Offline,
			// this is now the same local-first decision bootstrap() makes (OG1/finding-3 fix),
			// not a network-only re-guest that would strand the user on ConnectingScreen.
			startFreshGuestSession()
		}
	}
}

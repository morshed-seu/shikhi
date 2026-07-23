package com.shikhi.app.data.auth

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.api.dto.GuestRequest
import com.shikhi.app.data.db.ShikhiDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * OG2 (docs/94-offline-guest-bootstrap-design.md §3.3, ADR-0014): opportunistically registers the
 * real server guest account for a [SessionState.LocalGuest] session the moment connectivity
 * appears, then re-keys every local table's rows from [TokenStore.localGuestId] onto the
 * server-issued userId in one atomic transaction. Constrained/scheduled exactly like
 * [com.shikhi.app.data.outbox.OutboxSyncWorker] — see that class for the pattern this mirrors.
 *
 * Deviation from the design doc's pseudocode: [AuthInterceptor] attaches whatever token is
 * currently in [TokenStore] at request time, so `userApi.me()` must run *after*
 * [TokenStore.setSession], not before — calling it first would go out with no bearer token and
 * 401. To make retries safe (a crash between `setSession` and clearing [TokenStore.localGuestId]
 * must not re-provision a second server guest — ADR-0014/design doc §5: exactly one call per
 * device), [authApi.guest] is only called when there is no refresh token yet; a retry that
 * already has one resumes straight from `userApi.me()`.
 */
@HiltWorker
class GuestRegistrationWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val authApi: AuthApi,
	private val userApi: dagger.Lazy<UserApi>,
	private val tokenStore: TokenStore,
	private val db: ShikhiDatabase,
	private val authRepository: AuthRepository,
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result {
		val localId = tokenStore.localGuestId() ?: return Result.success()
		return try {
			if (tokenStore.currentRefreshToken() == null) {
				val pair = authApi.guest(GuestRequest(uiLocale()))
				tokenStore.setSession(pair.accessToken, pair.refreshToken)
			}
			val user = userApi.get().me()
			// ADR-0014 finding-4 fix: re-check localGuestId immediately before the rekey rather than
			// trusting the value captured at the top of doWork(). AuthRepository.activate()
			// (login/claim/register/startGuest reached from a LocalGuest session, e.g. GuestBanner's
			// "sign in to existing account") cancels this worker's unique work AND clears
			// localGuestId before switching the session to the newly-activated account -- but
			// cancelUniqueWork() cannot interrupt an execution already past the read above. If
			// localGuestId has been cleared or changed since this instance started, the local guest
			// it was about to register has been discarded (or superseded) out from under it -- bail
			// out without touching the database rather than silently re-keying a discarded guest's
			// progress onto whatever account is now active.
			if (tokenStore.localGuestId() != localId) {
				return Result.success()
			}
			db.withTransaction {
				db.wordProgressDao().rekey(localId, user.id)
				db.wordProgressDao().rekeyReview(localId, user.id)
				db.localPracticeSessionDao().rekey(localId, user.id)
				// local_practice_exercises has no userId column (keyed by sessionId) — nothing
				// to re-key there.
				// UO2: the durable stats projection is per-user state exactly like the tables
				// above — it must survive LocalGuest -> Active in the same atomic re-key, or a
				// guest's reconciled baseline XP/hearts/streak would silently reset to defaults
				// under the new server userId.
				db.localStatsProjectionDao().rekey(localId, user.id)
			}
			tokenStore.clearLocalGuestId()
			// ADR-0014 finding-1 fix: this worker is the sole authority for LocalGuest -> Active;
			// it only flips the session once the re-key transaction AND clearLocalGuestId() have
			// both completed, so Room never has a window where the session is Active but progress
			// is still under the old localGuestId.
			authRepository.completeGuestRegistration(user)
			Result.success()
		} catch (e: Exception) {
			Result.retry()
		}
	}

	private fun uiLocale() = if (Locale.getDefault().language == "en") "en" else "bn"

	companion object {
		// Not private (finding 3): AuthRepository.logout() references this same name to cancel
		// the pending unique work when logging out of a LocalGuest session.
		const val UNIQUE_NAME = "guest-registration"

		/**
		 * Enqueue (or keep) one network-constrained registration attempt. [ExistingWorkPolicy.KEEP]
		 * is load-bearing (design doc §7 risk 2): [AuthRepository.bootstrap] calls this on every
		 * process start while still [SessionState.LocalGuest], and it must not double-enqueue
		 * (and therefore double-register) across restarts.
		 */
		fun schedule(workManager: WorkManager) {
			val request = OneTimeWorkRequestBuilder<GuestRegistrationWorker>()
				.setConstraints(
					Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
				)
				.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
				.build()
			workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
		}
	}
}

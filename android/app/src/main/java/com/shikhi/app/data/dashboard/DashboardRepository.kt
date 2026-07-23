package com.shikhi.app.data.dashboard

import com.shikhi.app.data.api.DashboardApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.api.dto.DashboardResponse
import com.shikhi.app.data.api.dto.Identity
import com.shikhi.app.data.api.dto.UpdateProfileRequest
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.content.Sourced
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ContentCacheDao
import com.shikhi.app.data.progress.StatsProjectionRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Learner profile & dashboard (E13/MD3). The snapshot follows the same network-first,
 * Room-cache-fallback shape as [com.shikhi.app.data.content.CachedContentRepository] (MA4,
 * NFR-AN4) so the profile still renders offline with the "offline copy" indicator. Linked
 * identities, profile edits, export, and delete are network-only:
 * - identities failing must NOT blank the profile — same decision as the web client
 *   (frontend/src/components/ProfileView.tsx) — so a failed fetch soft-fails to `emptyList()`.
 * - edit/export/delete need the network and are simply left to throw; the UI disables them
 *   while the dashboard is showing a cached (offline) snapshot.
 *
 * UO4: a cached snapshot's `stats` is overlaid with the live local [StatsProjectionRepository]
 * projection before being returned, so offline play shows up immediately rather than only after
 * the next sync.
 */
@Singleton
class DashboardRepository @Inject constructor(
	private val dashboardApi: DashboardApi,
	private val userApi: UserApi,
	private val cache: ContentCacheDao,
	private val statsProjectionRepository: StatsProjectionRepository,
	private val authRepository: AuthRepository,
	private val tokenStore: TokenStore,
) {

	private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

	private val cacheKey = "dashboard"

	suspend fun dashboard(): Sourced<DashboardResponse>? {
		try {
			val fresh = dashboardApi.dashboard()
			cache.put(CachedPayload(cacheKey, json.encodeToString(DashboardResponse.serializer(), fresh), System.currentTimeMillis()))
			return Sourced(fresh, fromCache = false)
		} catch (e: Exception) {
			val cached = cache.get(cacheKey) ?: return null
			val value = runCatching { json.decodeFromString(DashboardResponse.serializer(), cached.json) }.getOrNull() ?: return null
			val overlaid = runCatching { value.copy(stats = statsProjectionRepository.overlay(currentUserId(), value.stats)) }
				.getOrDefault(value)
			return Sourced(overlaid, fromCache = true)
		}
	}

	/**
	 * Same shape as [com.shikhi.app.data.practice.LocalPracticeSource]'s / [com.shikhi.app.data.progress.LevelRepository]'s
	 * identically-named private helpers — deliberately duplicated per-class in this codebase
	 * rather than shared (see those methods' doc comments).
	 */
	private suspend fun currentUserId(): String = when (val state = authRepository.session.value) {
		is SessionState.Active -> state.user.id
		is SessionState.LocalGuest -> tokenStore.localGuestId()
			?: error("LocalGuest session with no stored localGuestId — invariant violated")
		else -> error("Dashboard stats overlay requires an already-authenticated or local-guest session")
	}

	/** Never throws — an identities failure must not blank the rest of the profile. */
	suspend fun identities(): List<Identity> = runCatching { dashboardApi.identities() }.getOrDefault(emptyList())

	suspend fun updateProfile(displayName: String? = null, uiLocale: String? = null): User =
		userApi.updateProfile(UpdateProfileRequest(displayName = displayName, uiLocale = uiLocale))

	/** Raw JSON text, byte-faithful (not re-encoded) — handed straight to the share sheet. */
	suspend fun exportRaw(): String = dashboardApi.export().use { it.string() }

	suspend fun deleteAccount() {
		dashboardApi.deleteAccount()
	}
}

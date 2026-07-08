package com.shikhi.app.data.dashboard

import com.shikhi.app.data.api.DashboardApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.api.dto.DashboardResponse
import com.shikhi.app.data.api.dto.Identity
import com.shikhi.app.data.api.dto.UpdateProfileRequest
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.content.Sourced
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ContentCacheDao
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
 */
@Singleton
class DashboardRepository @Inject constructor(
	private val dashboardApi: DashboardApi,
	private val userApi: UserApi,
	private val cache: ContentCacheDao,
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
			return Sourced(value, fromCache = true)
		}
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

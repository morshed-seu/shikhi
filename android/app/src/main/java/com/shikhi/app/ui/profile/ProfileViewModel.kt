package com.shikhi.app.ui.profile

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.data.api.dto.DashboardResponse
import com.shikhi.app.data.api.dto.Identity
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.dashboard.DashboardRepository
import com.shikhi.app.data.progress.StatsProjectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
	val loading: Boolean = true,
	/** Hard error: the dashboard snapshot itself could not be loaded (network + no cache). */
	val error: Boolean = false,
	/**
	 * OG-fix: a [SessionState.LocalGuest] has no access token yet, so a dashboard fetch is
	 * guaranteed to fail — this is shown instead of [error] while there's no cached snapshot to
	 * fall back to either, and clears itself automatically once [GuestRegistrationWorker]
	 * finishes and the session flips to `Active`.
	 */
	val guestSyncPending: Boolean = false,
	val dashboard: DashboardResponse? = null,
	/** A failed `GET /me/identities` soft-fails to `emptyList()` — never blanks the profile. */
	val identities: List<Identity> = emptyList(),
	val user: User? = null,
	val isGuest: Boolean = false,
	/** True when [dashboard] came from the offline Room cache (NFR-AN4). */
	val fromCache: Boolean = false,
	/**
	 * UO4: true when the local stats projection has never actually reconciled with the server —
	 * distinct from [fromCache] (which just means this particular dashboard fetch came from the
	 * Room cache). Drives [OfflineCopyBanner] instead of [fromCache].
	 */
	val neverSynced: Boolean = false,
	val editingName: Boolean = false,
	val nameDraft: String = "",
	val savingName: Boolean = false,
	val savingLocale: Boolean = false,
	/** Raw exported JSON, consumed once by the screen to launch the share sheet. */
	val exportPayload: String? = null,
	val exporting: Boolean = false,
	val exportError: Boolean = false,
	val confirmingDelete: Boolean = false,
	val deleting: Boolean = false,
	val deleteError: Boolean = false,
) {
	/** Profile edits and account actions all require the network (PRD 21 §8). */
	val editsEnabled: Boolean get() = !fromCache
}

/**
 * Profile & dashboard (E13/MD3): a dashboard snapshot (network-first, cache-fallback, mirrors
 * [DashboardRepository]) plus the account identity, exposed as one screen so the header can
 * drop its logout button and the practice-first home stays uncluttered (PRD 21 §8).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
	private val authRepository: AuthRepository,
	private val dashboardRepository: DashboardRepository,
	private val statsProjectionRepository: StatsProjectionRepository,
	private val tokenStore: TokenStore,
) : ViewModel() {

	private val _state = MutableStateFlow(ProfileUiState())
	val state: StateFlow<ProfileUiState> = _state

	init {
		viewModelScope.launch {
			var wasLocalGuest = authRepository.session.value is SessionState.LocalGuest
			authRepository.session.collect { session ->
				val user = (session as? SessionState.Active)?.user
				// OG1: a LocalGuest has no User object yet, but is definitely "a guest."
				_state.update { it.copy(user = user, isGuest = session is SessionState.LocalGuest || user?.isGuest == true) }
				val isLocalGuestNow = session is SessionState.LocalGuest
				// OG-fix: GuestRegistrationWorker just finished (LocalGuest -> Active) — the
				// dashboard fetch refresh() skipped while guestSyncPending is now safe to retry.
				if (wasLocalGuest && !isLocalGuestNow) refresh()
				wasLocalGuest = isLocalGuestNow
			}
		}
		refresh()
	}

	/** Pulls the dashboard snapshot + linked identities; called on entry. */
	fun refresh() {
		_state.update { it.copy(loading = true, error = false) }
		viewModelScope.launch {
			if (authRepository.session.value is SessionState.LocalGuest) {
				// OG-fix: no server account yet, so dashboardApi/identities would 401
				// unconditionally regardless of connectivity — skip the doomed network round
				// trip. A cached snapshot from a prior Active session (e.g. after logout ->
				// re-guest) is still shown if present; otherwise show the pending-sync state
				// rather than the generic hard error.
				_state.update { it.copy(loading = false, guestSyncPending = it.dashboard == null) }
				return@launch
			}
			val dash = async { dashboardRepository.dashboard() }
			// Fetched independently: a failed identities call must not hard-error the whole
			// profile — only the masked-email line is affected (same decision as the web
			// client, frontend/src/components/ProfileView.tsx). DashboardRepository.identities()
			// already soft-fails to emptyList(), but guard here too so a misbehaving
			// implementation can never sink the dashboard load with it.
			val ids = async { runCatching { dashboardRepository.identities() }.getOrDefault(emptyList()) }
			// UO4: whether the local projection has ever genuinely synced — drives neverSynced
			// below. Defaults to true (banner suppressed) if this throws, e.g. outside an
			// authenticated/local-guest session.
			val synced = async { runCatching { statsProjectionRepository.hasReconciled(currentUserId()) }.getOrDefault(true) }
			val d = dash.await()
			val identityList = ids.await()
			val neverSynced = !synced.await()
			_state.update {
				it.copy(
					loading = false,
					error = d == null && it.dashboard == null,
					guestSyncPending = false,
					dashboard = d?.value ?: it.dashboard,
					fromCache = d?.fromCache == true,
					identities = identityList,
					neverSynced = neverSynced,
				)
			}
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
		else -> error("Profile requires an already-authenticated or local-guest session")
	}

	fun startEditName() = _state.update { it.copy(editingName = true, nameDraft = it.user?.displayName ?: "") }

	fun setNameDraft(v: String) = _state.update { it.copy(nameDraft = v) }

	fun cancelEditName() = _state.update { it.copy(editingName = false) }

	fun saveName() {
		val s = _state.value
		if (s.savingName || !s.editsEnabled) return
		_state.update { it.copy(savingName = true) }
		viewModelScope.launch {
			runCatching { dashboardRepository.updateProfile(displayName = s.nameDraft.trim()) }
				.onSuccess { updated ->
					// Push the PATCHed user into the session too (US-13.2 "shows everywhere"):
					// this ViewModel is back-stack-entry-scoped, so a later Profile visit
					// re-seeds from the session — a local-only update would resurrect the old
					// name there.
					authRepository.updateActiveUser(updated)
					_state.update { it.copy(user = updated, editingName = false) }
				}
			_state.update { it.copy(savingName = false) }
		}
	}

	/**
	 * Applies the UI locale immediately (AppCompatDelegate — backports per-app language to
	 * `minSdk`, matches `res/xml/locales_config.xml`) and persists it to the account via the
	 * same `PATCH /me` the display-name edit and guest-claim flows use.
	 */
	fun setLocale(locale: String) {
		val s = _state.value
		if (s.savingLocale || !s.editsEnabled || s.user?.uiLocale == locale) return
		_state.update { it.copy(savingLocale = true) }
		AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale))
		viewModelScope.launch {
			runCatching { dashboardRepository.updateProfile(uiLocale = locale) }
				.onSuccess { updated ->
					// Same session propagation as saveName (US-13.2).
					authRepository.updateActiveUser(updated)
					_state.update { it.copy(user = updated) }
				}
			_state.update { it.copy(savingLocale = false) }
		}
	}

	fun startExport() {
		val s = _state.value
		if (s.exporting || !s.editsEnabled) return
		_state.update { it.copy(exporting = true, exportError = false) }
		viewModelScope.launch {
			runCatching { dashboardRepository.exportRaw() }
				.onSuccess { payload -> _state.update { it.copy(exportPayload = payload) } }
				.onFailure { _state.update { it.copy(exportError = true) } }
			_state.update { it.copy(exporting = false) }
		}
	}

	/** Called once the screen has handed [ProfileUiState.exportPayload] off to the share sheet. */
	fun consumeExport() = _state.update { it.copy(exportPayload = null) }

	fun startDeleteConfirm() = _state.update { it.copy(confirmingDelete = true, deleteError = false) }

	fun cancelDelete() = _state.update { it.copy(confirmingDelete = false) }

	/** Never a system dialog — the inline confirm step above is the only guard. */
	fun deleteAccount() {
		val s = _state.value
		if (s.deleting || !s.editsEnabled) return
		_state.update { it.copy(deleting = true, deleteError = false) }
		viewModelScope.launch {
			runCatching { dashboardRepository.deleteAccount() }
				.onSuccess { logout() }
				.onFailure { _state.update { it.copy(deleteError = true) } }
			_state.update { it.copy(deleting = false) }
		}
	}

	/** Same path the home header used before logout moved here (PRD 21 §8). */
	fun logout() {
		viewModelScope.launch { authRepository.logout() }
	}
}

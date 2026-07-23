package com.shikhi.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.dto.CurriculumTree
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.content.CachedContentRepository
import com.shikhi.app.data.progress.LevelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

enum class BackendHealth { CHECKING, ONLINE, WARMING, OFFLINE }

data class HomeUiState(
	val stats: Stats? = null,
	val tree: CurriculumTree? = null,
	val curriculumLoading: Boolean = true,
	val curriculumError: Boolean = false,
	val health: BackendHealth = BackendHealth.CHECKING,
	val isGuest: Boolean = false,
	val savingLevel: Boolean = false,
	/** Set when a self-placement PUT fails so the hero can surface it instead of doing nothing. */
	val levelError: Boolean = false,
	/** True when what's on screen came from the offline cache (NFR-AN4). */
	val fromCache: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
	private val authRepository: AuthRepository,
	private val authApi: AuthApi,
	private val content: CachedContentRepository,
	private val levelRepository: LevelRepository,
) : ViewModel() {

	private val _state = MutableStateFlow(HomeUiState())
	val state: StateFlow<HomeUiState> = _state

	init {
		checkHealth()
		viewModelScope.launch {
			authRepository.session.collect { session ->
				// OG1: a LocalGuest is definitely "a guest" — just not yet server-registered.
				_state.update {
					it.copy(isGuest = session is SessionState.LocalGuest || (session as? SessionState.Active)?.user?.isGuest == true)
				}
			}
		}
	}

	/**
	 * Self-placement from the practice hero's level picker. UO3: routed entirely through
	 * [LevelRepository] (durable projection + `"stats"` cache write + a buffered
	 * [com.shikhi.app.data.outbox.OutboxEventType.SET_LEVEL] outbox event) instead of a direct
	 * `PUT /stats/level` call, so it works offline — the outbox
	 * worker delivers it to the server whenever connectivity allows. [LevelRepository.setLevel]
	 * makes no network call, so it should not realistically fail from a reachable UI state, but
	 * the `runCatching`/`onFailure` shape is kept for symmetry with the rest of this class.
	 */
	fun setLevel(level: String) {
		if (_state.value.savingLevel) return
		_state.update { it.copy(savingLevel = true, levelError = false) }
		viewModelScope.launch {
			runCatching { levelRepository.setLevel(level) }
				.onSuccess { _state.update { s -> s.copy(stats = s.stats?.copy(cefrLevel = level), levelError = false) } }
				.onFailure { _state.update { it.copy(levelError = true) } }
			_state.update { it.copy(savingLevel = false) }
		}
	}

	/**
	 * Pulls stats + curriculum (network-first, offline cache fallback); called on entry
	 * and again when returning from a lesson.
	 */
	fun refresh() {
		viewModelScope.launch {
			val stats = async { content.stats() }
			val tree = async { content.curriculum() }
			stats.await()?.let { s -> _state.update { it.copy(stats = s.value) } }
			val t = tree.await()
			_state.update {
				it.copy(
					tree = t?.value ?: it.tree,
					curriculumLoading = false,
					curriculumError = t == null && it.tree == null,
					fromCache = t?.fromCache == true,
				)
			}
		}
	}

	/**
	 * The free-tier backend cold-starts in ~50s (DEPLOY.md): early failures show as
	 * "warming up" and retry with backoff before being declared offline.
	 */
	private fun checkHealth() {
		viewModelScope.launch {
			val delays = listOf(0L, 5_000L, 10_000L, 15_000L, 20_000L)
			for ((attempt, wait) in delays.withIndex()) {
				delay(wait)
				try {
					if (authApi.health().isSuccessful) {
						_state.update { it.copy(health = BackendHealth.ONLINE) }
						return@launch
					}
				} catch (_: IOException) {
					// fall through to warming/retry
				}
				if (attempt < delays.lastIndex) {
					_state.update { it.copy(health = BackendHealth.WARMING) }
				}
			}
			_state.update { it.copy(health = BackendHealth.OFFLINE) }
		}
	}
}

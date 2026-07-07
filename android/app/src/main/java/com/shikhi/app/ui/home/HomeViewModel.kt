package com.shikhi.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.ContentApi
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.CurriculumTree
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.auth.AuthRepository
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
	private val authRepository: AuthRepository,
	private val authApi: AuthApi,
	private val contentApi: ContentApi,
	private val progressApi: ProgressApi,
) : ViewModel() {

	private val _state = MutableStateFlow(HomeUiState())
	val state: StateFlow<HomeUiState> = _state

	init {
		checkHealth()
	}

	/** Pulls stats + curriculum; called on entry and again when returning from a lesson. */
	fun refresh() {
		viewModelScope.launch {
			val stats = async { runCatching { progressApi.stats() }.getOrNull() }
			val tree = async { runCatching { contentApi.curriculum() } }
			_state.update { it.copy(stats = stats.await() ?: it.stats) }
			tree.await().fold(
				onSuccess = { t -> _state.update { it.copy(tree = t, curriculumLoading = false, curriculumError = false) } },
				onFailure = { _ -> _state.update { it.copy(curriculumLoading = false, curriculumError = it.tree == null) } },
			)
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

	fun logout() {
		viewModelScope.launch { authRepository.logout() }
	}
}

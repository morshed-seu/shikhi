package com.shikhi.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

enum class BackendHealth { CHECKING, ONLINE, WARMING, OFFLINE }

data class HomeUiState(
	val displayName: String? = null,
	val health: BackendHealth = BackendHealth.CHECKING,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
	private val authRepository: AuthRepository,
	private val authApi: AuthApi,
) : ViewModel() {

	private val _state = MutableStateFlow(HomeUiState())
	val state: StateFlow<HomeUiState> = _state

	init {
		viewModelScope.launch {
			authRepository.session.collect { session ->
				if (session is SessionState.Active) {
					_state.update { it.copy(displayName = session.user.displayName?.takeIf(String::isNotBlank)) }
				}
			}
		}
		checkHealth()
	}

	/**
	 * The free-tier backend cold-starts in ~50s (DEPLOY.md): failures early on show as
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

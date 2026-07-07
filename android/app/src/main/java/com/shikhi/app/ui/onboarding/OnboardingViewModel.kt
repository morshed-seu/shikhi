package com.shikhi.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class OnboardingUiState { Idle, Busy, Error }

@HiltViewModel
class OnboardingViewModel @Inject constructor(
	private val authRepository: AuthRepository,
) : ViewModel() {

	private val _state = MutableStateFlow(OnboardingUiState.Idle)
	val state: StateFlow<OnboardingUiState> = _state

	fun startAsGuest() {
		if (_state.value == OnboardingUiState.Busy) return
		_state.value = OnboardingUiState.Busy
		viewModelScope.launch {
			try {
				val uiLocale = if (Locale.getDefault().language == "en") "en" else "bn"
				authRepository.startGuest(uiLocale)
				// Success flips the app-level SessionState; this screen goes away.
			} catch (e: Exception) {
				_state.value = OnboardingUiState.Error
			}
		}
	}
}

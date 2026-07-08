package com.shikhi.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.data.api.apiError
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.LoginPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.Locale
import javax.inject.Inject

enum class AuthMode { LOGIN, REGISTER }

data class OnboardingUiState(
	val mode: AuthMode = AuthMode.LOGIN,
	val formOpen: Boolean = false,
	val email: String = "",
	val password: String = "",
	val displayName: String = "",
	val rememberMe: Boolean = false,
	val busy: Boolean = false,
	/** Server-sent localized message, or null → generic error string. */
	val errorMessage: String? = null,
	val error: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
	private val authRepository: AuthRepository,
	private val loginPrefs: LoginPrefs,
) : ViewModel() {

	private val _state = MutableStateFlow(OnboardingUiState())
	val state: StateFlow<OnboardingUiState> = _state

	init {
		loadRememberedEmail()
	}

	private fun uiLocale() = if (Locale.getDefault().language == "en") "en" else "bn"

	fun setMode(mode: AuthMode) = _state.update { it.copy(mode = mode, error = false, errorMessage = null) }

	fun toggleForm() = _state.update { it.copy(formOpen = !it.formOpen) }

	fun setEmail(v: String) = _state.update { it.copy(email = v) }

	fun setPassword(v: String) = _state.update { it.copy(password = v) }

	fun setDisplayName(v: String) = _state.update { it.copy(displayName = v) }

	fun setRememberMe(v: Boolean) = _state.update { it.copy(rememberMe = v) }

	/** Prefill the login form with the last remembered email (checkbox ticked to match). */
	private fun loadRememberedEmail() = viewModelScope.launch {
		val saved = loginPrefs.rememberedEmail() ?: return@launch
		_state.update { it.copy(email = saved, rememberMe = true) }
	}

	fun startAsGuest() = submit { authRepository.startGuest(uiLocale()) }

	fun submitForm() {
		val s = _state.value
		if (s.email.isBlank() || s.password.isBlank()) return
		submit {
			when (s.mode) {
				AuthMode.LOGIN -> {
					authRepository.login(s.email, s.password)
					// Remember (or forget) the email only after the credentials are accepted.
					loginPrefs.setRememberedEmail(if (s.rememberMe) s.email else null)
				}
				AuthMode.REGISTER -> authRepository.register(
					s.email,
					s.password,
					s.displayName.ifBlank { null },
					uiLocale(),
				)
			}
		}
	}

	private fun submit(action: suspend () -> Unit) {
		if (_state.value.busy) return
		_state.update { it.copy(busy = true, error = false, errorMessage = null) }
		viewModelScope.launch {
			try {
				action()
				// Success flips the app-level SessionState and this screen goes away, but the
				// ViewModel is Activity-scoped so it outlives the screen. Reset to a clean form
				// so a later logout returns to onboarding without a stale busy flag or fields —
				// then re-prefill the remembered email for that next visit.
				_state.value = OnboardingUiState()
				loadRememberedEmail()
			} catch (e: Exception) {
				_state.update {
					it.copy(
						busy = false,
						error = true,
						errorMessage = (e as? HttpException)?.apiError()?.message,
					)
				}
			}
		}
	}
}

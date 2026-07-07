package com.shikhi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
	private val authRepository: AuthRepository,
) : ViewModel() {

	val session: StateFlow<SessionState> = authRepository.session

	init {
		viewModelScope.launch { authRepository.bootstrap() }
	}
}

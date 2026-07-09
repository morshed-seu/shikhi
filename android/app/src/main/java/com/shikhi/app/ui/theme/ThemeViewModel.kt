package com.shikhi.app.ui.theme

import androidx.lifecycle.ViewModel
import com.shikhi.app.data.prefs.ThemeMode
import com.shikhi.app.data.prefs.ThemePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
	private val themePrefs: ThemePrefs,
) : ViewModel() {

	val mode: StateFlow<ThemeMode> = themePrefs.mode

	fun setMode(m: ThemeMode) = themePrefs.setMode(m)
}

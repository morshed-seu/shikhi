package com.shikhi.app.ui.theme

import com.shikhi.app.data.prefs.ThemeMode
import com.shikhi.app.data.prefs.ThemePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeViewModelTest {

	private class FakeThemePrefs(initial: ThemeMode = ThemeMode.SYSTEM) : ThemePrefs {
		private val _mode = MutableStateFlow(initial)
		override val mode: StateFlow<ThemeMode> = _mode
		var setModeCalls = 0
			private set

		override fun setMode(mode: ThemeMode) {
			setModeCalls++
			_mode.value = mode
		}
	}

	@Test
	fun `setMode delegates to prefs and mode reflects the update`() {
		val prefs = FakeThemePrefs(ThemeMode.SYSTEM)
		val viewModel = ThemeViewModel(prefs)

		assertEquals(ThemeMode.SYSTEM, viewModel.mode.value)

		viewModel.setMode(ThemeMode.DARK)

		assertEquals(1, prefs.setModeCalls)
		assertEquals(ThemeMode.DARK, viewModel.mode.value)
	}
}

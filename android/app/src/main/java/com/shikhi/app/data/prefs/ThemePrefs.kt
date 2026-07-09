package com.shikhi.app.data.prefs

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Pure + top-level so it's unit-testable without a Context. */
fun ThemeMode.toNightMode(): Int = when (this) {
	ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
	ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
	ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
}

interface ThemePrefs {
	val mode: StateFlow<ThemeMode>
	fun setMode(mode: ThemeMode)
}

/**
 * Backed by SharedPreferences, not the session DataStore [com.shikhi.app.data.auth.LoginPrefs]
 * uses: the stored mode has to be read SYNCHRONOUSLY in Application.onCreate, before the first
 * frame, so the correct night mode is set before anything is drawn. DataStore reads are
 * suspending — gating app startup on one to avoid a flash of the wrong theme isn't worth it for
 * three possible values.
 */
@Singleton
class SharedPrefsThemePrefs @Inject constructor(
	@ApplicationContext context: Context,
) : ThemePrefs {

	private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)

	private val _mode = MutableStateFlow(readStored())
	override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

	private fun readStored(): ThemeMode {
		val stored = prefs.getString(KEY, null) ?: return ThemeMode.SYSTEM
		return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
	}

	override fun setMode(mode: ThemeMode) {
		prefs.edit().putString(KEY, mode.name).apply()
		_mode.value = mode
		AppCompatDelegate.setDefaultNightMode(mode.toNightMode())
	}

	private companion object {
		const val KEY = "theme_mode"
	}
}

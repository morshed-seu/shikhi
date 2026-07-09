package com.shikhi.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.shikhi.app.data.outbox.OutboxForegroundFlusher
import com.shikhi.app.data.prefs.ThemePrefs
import com.shikhi.app.data.prefs.toNightMode
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShikhiApplication : Application(), Configuration.Provider {

	@Inject
	lateinit var outboxFlusher: OutboxForegroundFlusher

	@Inject
	lateinit var workerFactory: HiltWorkerFactory

	@Inject
	lateinit var themePrefs: ThemePrefs

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

	override fun onCreate() {
		super.onCreate()
		outboxFlusher.register()
		// Must happen before any Activity is created — the night mode has to be settled
		// before ShikhiTheme's first composition, or the first frame flashes the wrong theme.
		AppCompatDelegate.setDefaultNightMode(themePrefs.mode.value.toNightMode())
	}
}

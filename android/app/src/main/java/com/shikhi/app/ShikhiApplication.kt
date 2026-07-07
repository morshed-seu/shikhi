package com.shikhi.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.shikhi.app.data.outbox.OutboxForegroundFlusher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShikhiApplication : Application(), Configuration.Provider {

	@Inject
	lateinit var outboxFlusher: OutboxForegroundFlusher

	@Inject
	lateinit var workerFactory: HiltWorkerFactory

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

	override fun onCreate() {
		super.onCreate()
		outboxFlusher.register()
	}
}

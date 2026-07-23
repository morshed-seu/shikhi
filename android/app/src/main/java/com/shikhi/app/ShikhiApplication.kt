package com.shikhi.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.shikhi.app.data.content.seed.ContentSeedWorker
import com.shikhi.app.data.outbox.OutboxForegroundFlusher
import com.shikhi.app.data.progress.ProgressPullWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShikhiApplication : Application(), Configuration.Provider {

	@Inject
	lateinit var outboxFlusher: OutboxForegroundFlusher

	@Inject
	lateinit var workerFactory: HiltWorkerFactory

	// dagger.Lazy, not eager @Inject — matches OutboxRepository's own WorkManager injection,
	// avoiding a WorkManager.getInstance() call racing Application.onCreate() before this
	// object is fully attached as the process's Configuration.Provider.
	@Inject
	lateinit var workManager: dagger.Lazy<WorkManager>

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

	override fun onCreate() {
		super.onCreate()
		outboxFlusher.register()
		// OF2: import the bundled vocabulary/curriculum seed off the main thread, once, at
		// startup — mirrors OutboxSyncWorker's own WorkManager registration mechanism.
		ContentSeedWorker.schedule(workManager.get())
		// UO6: periodic safety-net pull, alongside the one-shot triggers on login/guest
		// registration (AuthRepository.login, GuestRegistrationWorker.doWork).
		ProgressPullWorker.schedulePeriodic(workManager.get())
	}
}

package com.shikhi.app

import android.app.Application
import com.shikhi.app.data.outbox.OutboxForegroundFlusher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShikhiApplication : Application() {

	@Inject
	lateinit var outboxFlusher: OutboxForegroundFlusher

	override fun onCreate() {
		super.onCreate()
		outboxFlusher.register()
	}
}

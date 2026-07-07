package com.shikhi.app.data.outbox

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Deferred outbox flush (MA4): WorkManager retries the idempotent sync batch with
 * exponential backoff once the network is back — the durable counterpart of the
 * foreground flush, matching the web outbox's reconnect trigger (frontend App.tsx).
 */
@HiltWorker
class OutboxSyncWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val outbox: OutboxRepository,
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result =
		if (outbox.flush()) Result.success() else Result.retry()

	companion object {
		private const val UNIQUE_NAME = "outbox-sync"

		/** Enqueue (or keep) one network-constrained sync attempt. */
		fun schedule(workManager: WorkManager) {
			val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
				.setConstraints(
					Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
				)
				.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
				.build()
			workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
		}
	}
}

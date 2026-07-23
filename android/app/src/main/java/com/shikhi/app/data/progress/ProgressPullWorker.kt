package com.shikhi.app.data.progress

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Deferred progress pull (UO6): mirrors [com.shikhi.app.data.outbox.OutboxSyncWorker]'s shape —
 * WorkManager retries [ProgressPullRepository.pull] with exponential backoff once the network is
 * back. Triggered one-shot after login into an existing account and after guest registration
 * completes, plus a periodic safety-net pull (see [schedulePeriodic]).
 */
@HiltWorker
class ProgressPullWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val pull: ProgressPullRepository,
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result =
		if (pull.pull()) Result.success() else Result.retry()

	companion object {
		// Not private (mirrors GuestRegistrationWorker.UNIQUE_NAME): AuthRepositoryTest/
		// GuestRegistrationWorkerTest reference this to verify the pull was scheduled.
		const val UNIQUE_NAME = "progress-pull"
		private const val PERIODIC_UNIQUE_NAME = "progress-pull-periodic"

		/** Enqueue (or keep) one network-constrained pull attempt. */
		fun schedule(workManager: WorkManager) {
			val request = OneTimeWorkRequestBuilder<ProgressPullWorker>()
				.setConstraints(
					Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
				)
				.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
				.build()
			workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
		}

		/**
		 * Periodic safety-net pull (every 6 hours) — a distinct unique name from [schedule]'s
		 * one-shot: WorkManager rejects mixing one-time and periodic requests under the same
		 * unique work name.
		 */
		fun schedulePeriodic(workManager: WorkManager) {
			val request = PeriodicWorkRequestBuilder<ProgressPullWorker>(6, TimeUnit.HOURS)
				.setConstraints(
					Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
				)
				.build()
			workManager.enqueueUniquePeriodicWork(PERIODIC_UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
		}
	}
}

package com.shikhi.app.data.content.seed

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-time bundled-content import at app startup (OF2, docs/93-offline-learning-design.md §6),
 * mirroring [com.shikhi.app.data.outbox.OutboxSyncWorker]'s exact registration mechanism
 * (`WorkManager.enqueueUniqueWork` with [ExistingWorkPolicy.KEEP], scheduled from
 * `ShikhiApplication.onCreate`) rather than inventing a new startup hook. No network
 * constraint — [ContentSeedImporter] is pure asset I/O + local DB writes, so it should run
 * immediately, including with the device fully offline (that's the whole point of OF2).
 *
 * [ContentSeedImporter.importIfNeeded] already no-ops once the bundled seed is current
 * ([CONTENT_SEED_VERSION] gate in `sessionDataStore`), so re-running this worker on every app
 * start (e.g. after a process death mid-import) is safe and cheap.
 */
@HiltWorker
class ContentSeedWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val importer: ContentSeedImporter,
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result =
		try {
			importer.importIfNeeded()
			Result.success()
		} catch (e: Exception) {
			Result.retry()
		}

	companion object {
		private const val UNIQUE_NAME = "content-seed-import"

		/** Enqueue (or keep) one bundled-content import attempt. */
		fun schedule(workManager: WorkManager) {
			val request = OneTimeWorkRequestBuilder<ContentSeedWorker>().build()
			workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
		}
	}
}

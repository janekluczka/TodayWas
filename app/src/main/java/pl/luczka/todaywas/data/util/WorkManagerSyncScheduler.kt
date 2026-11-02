package pl.luczka.todaywas.data.util

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.time.Duration
import javax.inject.Inject
import pl.luczka.todaywas.data.worker.SyncWorker

private const val SYNC_WORK_NAME = "sync-local-data"

class WorkManagerSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) : SyncScheduler {

    // KEEP rather than REPLACE: SyncWorker re-reads DB state at execution time rather than
    // snapshotting at enqueue time, so a redundant enqueue while one is already pending/running
    // adds nothing, and KEEP avoids cancelling a request that's mid-flight on a slow network call.
    override fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                Duration.ofMillis(WorkRequest.MIN_BACKOFF_MILLIS),
            ).build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

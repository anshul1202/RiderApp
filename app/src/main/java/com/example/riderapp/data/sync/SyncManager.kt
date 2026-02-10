package com.example.riderapp.data.sync

import android.util.Log
import androidx.work.*
import com.example.riderapp.monitoring.MonitoringService
import com.example.riderapp.util.Constants
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val workManager: WorkManager,
    private val syncConfig: SyncConfig,
    private val monitoringService: MonitoringService
) {
    companion object {
        private const val TAG = "SyncManager"
    }

    /**
     * Schedule periodic sync.
     * Interval and backoff are driven by [SyncConfig].
     * Only runs when network is available.
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            syncConfig.periodicSyncIntervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                syncConfig.workerInitialBackoffMs,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            Constants.PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )

        Log.i(TAG, "Periodic sync scheduled every ${syncConfig.periodicSyncIntervalMinutes} min, " +
                "backoff=${syncConfig.workerInitialBackoffMs}ms exponential")
        monitoringService.logEvent("periodic_sync_scheduled", mapOf(
            "interval_minutes" to syncConfig.periodicSyncIntervalMinutes,
            "worker_backoff_ms" to syncConfig.workerInitialBackoffMs
        ))
    }

    /**
     * Trigger an immediate one-time sync.
     * Uses KEEP policy to avoid duplicate work.
     */
    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                syncConfig.workerInitialBackoffMs,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            Constants.SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            oneTimeWorkRequest
        )

        Log.i(TAG, "Immediate sync triggered")
        monitoringService.logEvent("immediate_sync_triggered")
    }

    /**
     * Cancel all sync work.
     */
    fun cancelAllSync() {
        workManager.cancelUniqueWork(Constants.SYNC_WORK_NAME)
        workManager.cancelUniqueWork(Constants.PERIODIC_SYNC_WORK_NAME)
        Log.i(TAG, "All sync work cancelled")
        monitoringService.logEvent("sync_cancelled")
    }
}

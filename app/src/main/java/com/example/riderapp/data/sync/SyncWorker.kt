package com.example.riderapp.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.riderapp.domain.model.SyncResult
import com.example.riderapp.domain.repository.TaskRepository
import com.example.riderapp.monitoring.MonitoringService
import com.example.riderapp.monitoring.SyncMonitor
import com.example.riderapp.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: TaskRepository,
    private val syncConfig: SyncConfig,
    private val monitoringService: MonitoringService,
    private val syncMonitor: SyncMonitor
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "SyncWorker started, attempt: $runAttemptCount/${syncConfig.maxWorkerRetries}")

        monitoringService.logWorkerStatus("SyncWorker", "started", mapOf(
            "attempt" to runAttemptCount,
            "max_worker_retries" to syncConfig.maxWorkerRetries
        ))

        return try {
            // Step 1: Push local actions to server (batched + retried internally)
            val syncResult = repository.syncActions()

            when (syncResult) {
                is SyncResult.Success -> {
                    syncMonitor.onSyncSuccess(syncResult.syncedCount)
                    Log.i(TAG, "Sync successful: ${syncResult.syncedCount} actions synced")

                    // Step 2: Refresh tasks from server (server wins)
                    try {
                        repository.fetchTasksFromServer(Constants.RIDER_ID)
                        Log.i(TAG, "Tasks refreshed from server")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to refresh tasks from server: ${e.message}")
                        monitoringService.logError(e, mapOf("operation" to "refresh_after_sync"))
                    }

                    monitoringService.logWorkerStatus("SyncWorker", "completed_success")
                    Result.success()
                }

                is SyncResult.PartialSuccess -> {
                    syncMonitor.onSyncPartialSuccess(syncResult.syncedCount, syncResult.failedCount)
                    Log.w(TAG, "Sync partially successful: ${syncResult.syncedCount} synced, " +
                            "${syncResult.failedCount} failed")

                    monitoringService.logWorkerStatus("SyncWorker", "completed_partial", mapOf(
                        "synced" to syncResult.syncedCount,
                        "failed" to syncResult.failedCount,
                        "errors" to syncResult.errors.joinToString("; ")
                    ))

                    // Retry at worker level for remaining failures
                    if (runAttemptCount < syncConfig.maxWorkerRetries) {
                        Result.retry()
                    } else {
                        monitoringService.alertCritical(
                            "SyncWorker exhausted retries with partial failures",
                            mapOf("failed_count" to syncResult.failedCount)
                        )
                        Result.success() // Don't block future syncs
                    }
                }

                is SyncResult.Failure -> {
                    syncMonitor.onSyncFailure(syncResult.error)
                    Log.e(TAG, "Sync failed: ${syncResult.error}")

                    monitoringService.logWorkerStatus("SyncWorker", "failed", mapOf(
                        "error" to syncResult.error,
                        "attempt" to runAttemptCount
                    ))

                    if (runAttemptCount < syncConfig.maxWorkerRetries) {
                        Result.retry()
                    } else {
                        monitoringService.alertCritical(
                            "SyncWorker exhausted all retries",
                            mapOf("error" to syncResult.error, "attempts" to runAttemptCount)
                        )
                        Result.failure()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker crashed", e)
            monitoringService.logError(e, mapOf(
                "operation" to "SyncWorker.doWork",
                "attempt" to runAttemptCount
            ))
            monitoringService.logWorkerStatus("SyncWorker", "crashed", mapOf(
                "error" to (e.message ?: "Unknown"),
                "attempt" to runAttemptCount
            ))

            syncMonitor.onSyncFailure("Worker crashed: ${e.message}")

            if (runAttemptCount < syncConfig.maxWorkerRetries) {
                Result.retry()
            } else {
                monitoringService.alertCritical(
                    "SyncWorker crashed and exhausted retries",
                    mapOf("error" to (e.message ?: "Unknown"))
                )
                Result.failure()
            }
        }
    }
}

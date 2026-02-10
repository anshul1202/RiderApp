package com.example.riderapp.monitoring

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors sync health and reports anomalies.
 * Tracks sync patterns and alerts on failures exceeding thresholds.
 */
@Singleton
class SyncMonitor @Inject constructor(
    private val monitoringService: MonitoringService
) {
    companion object {
        private const val TAG = "SyncMonitor"
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val STALE_THRESHOLD_MS = 30 * 60 * 1000L // 30 minutes
    }

    private var consecutiveFailures = 0
    private var lastSuccessfulSync: Long = 0L
    private var totalSyncs = 0
    private var totalFailures = 0

    fun onSyncSuccess(syncedCount: Int) {
        consecutiveFailures = 0
        lastSuccessfulSync = System.currentTimeMillis()
        totalSyncs++

        monitoringService.logEvent("sync_success", mapOf(
            "synced_count" to syncedCount,
            "total_syncs" to totalSyncs
        ))

        Log.i(TAG, "Sync successful: $syncedCount items synced")
    }

    fun onSyncPartialSuccess(syncedCount: Int, failedCount: Int) {
        lastSuccessfulSync = System.currentTimeMillis()
        totalSyncs++
        totalFailures += failedCount

        monitoringService.logEvent("sync_partial_success", mapOf(
            "synced_count" to syncedCount,
            "failed_count" to failedCount,
            "total_failures" to totalFailures
        ))

        if (failedCount > syncedCount) {
            monitoringService.alertCritical(
                "High sync failure rate",
                mapOf("synced" to syncedCount, "failed" to failedCount)
            )
        }

        Log.w(TAG, "Sync partially successful: $syncedCount synced, $failedCount failed")
    }

    fun onSyncFailure(error: String) {
        consecutiveFailures++
        totalFailures++
        totalSyncs++

        monitoringService.logError(
            RuntimeException("Sync failure: $error"),
            mapOf(
                "consecutive_failures" to consecutiveFailures,
                "total_failures" to totalFailures
            )
        )

        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            monitoringService.alertCritical(
                "Consecutive sync failures exceeded threshold",
                mapOf(
                    "consecutive_failures" to consecutiveFailures,
                    "threshold" to MAX_CONSECUTIVE_FAILURES,
                    "last_error" to error
                )
            )
        }

        Log.e(TAG, "Sync failed ($consecutiveFailures consecutive): $error")
    }

    fun checkSyncHealth(): SyncHealthStatus {
        val now = System.currentTimeMillis()
        val timeSinceLastSync = if (lastSuccessfulSync > 0) now - lastSuccessfulSync else Long.MAX_VALUE

        return when {
            consecutiveFailures >= MAX_CONSECUTIVE_FAILURES -> SyncHealthStatus.CRITICAL
            timeSinceLastSync > STALE_THRESHOLD_MS -> SyncHealthStatus.STALE
            consecutiveFailures > 0 -> SyncHealthStatus.DEGRADED
            else -> SyncHealthStatus.HEALTHY
        }
    }

    fun getHealthSummary(): Map<String, Any> {
        return mapOf(
            "status" to checkSyncHealth().name,
            "consecutive_failures" to consecutiveFailures,
            "total_syncs" to totalSyncs,
            "total_failures" to totalFailures,
            "last_successful_sync" to lastSuccessfulSync
        )
    }
}

enum class SyncHealthStatus {
    HEALTHY,
    DEGRADED,
    STALE,
    CRITICAL
}

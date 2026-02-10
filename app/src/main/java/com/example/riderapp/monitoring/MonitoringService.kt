package com.example.riderapp.monitoring

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock monitoring service simulating Sentry/Datadog integration.
 * In production, this would send events to actual monitoring platforms.
 */
@Singleton
class MonitoringService @Inject constructor() {

    companion object {
        private const val TAG = "MonitoringService"
        private const val SENTRY_TAG = "SENTRY"
        private const val DATADOG_TAG = "DATADOG"
        private const val METRICS_TAG = "METRICS"
    }

    /**
     * Log a general event (simulates Datadog custom event / Sentry breadcrumb)
     */
    fun logEvent(event: String, properties: Map<String, Any> = emptyMap()) {
        Log.i(DATADOG_TAG, "Event: $event | Properties: $properties")
    }

    /**
     * Log an error (simulates Sentry error capture)
     */
    fun logError(throwable: Throwable, context: Map<String, Any> = emptyMap()) {
        Log.e(SENTRY_TAG, "Error: ${throwable.message} | Context: $context", throwable)
    }

    /**
     * Log sync metrics (simulates Datadog metrics)
     */
    fun logSyncMetric(syncedCount: Int, failedCount: Int, totalCount: Int) {
        Log.i(METRICS_TAG, "Sync Metrics - Synced: $syncedCount, Failed: $failedCount, Total: $totalCount")
        logEvent("sync_metrics", mapOf(
            "synced_count" to syncedCount,
            "failed_count" to failedCount,
            "total_count" to totalCount,
            "success_rate" to if (totalCount > 0) (syncedCount.toFloat() / totalCount * 100) else 0f
        ))
    }

    /**
     * Log API call metrics (simulates Datadog APM)
     */
    fun logApiCall(endpoint: String, responseCode: Int, durationMs: Long) {
        Log.i(DATADOG_TAG, "API Call: $endpoint | Status: $responseCode | Duration: ${durationMs}ms")
    }

    /**
     * Log WorkManager status (simulates custom dashboard metric)
     */
    fun logWorkerStatus(workerName: String, status: String, details: Map<String, Any> = emptyMap()) {
        Log.i(METRICS_TAG, "Worker: $workerName | Status: $status | Details: $details")
    }

    /**
     * Alert for critical failures (simulates PagerDuty/OpsGenie alert)
     */
    fun alertCritical(message: String, context: Map<String, Any> = emptyMap()) {
        Log.e(SENTRY_TAG, "CRITICAL ALERT: $message | Context: $context")
        // In production: trigger PagerDuty/OpsGenie alert
    }
}

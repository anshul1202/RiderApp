package com.example.riderapp.data.sync

/**
 * Configurable parameters for the sync engine.
 *
 * All values have sensible defaults and can be overridden
 * via DI (e.g. from remote config, build flavors, or tests).
 *
 * Backoff formula:  delay = initialBackoffMs * (backoffMultiplier ^ attempt)
 *                   capped at maxBackoffMs
 *
 * Example with defaults (initial=1s, multiplier=2.0, max=60s):
 *   Attempt 1 → 1 s
 *   Attempt 2 → 2 s
 *   Attempt 3 → 4 s
 *   Attempt 4 → 8 s
 *   Attempt 5 → 16 s  (maxRetries reached → give up on this batch)
 */
data class SyncConfig(

    // ── Batch processing ──────────────────────────────────────

    /** Number of unsynced actions to send in each API call. */
    val batchSize: Int = 50,

    // ── Per-batch retry with exponential backoff ──────────────

    /** Max retries per individual batch before giving up on it. */
    val maxRetriesPerBatch: Int = 3,

    /** Initial delay before the first retry (milliseconds). */
    val initialBackoffMs: Long = 1_000,

    /** Multiplier applied to the delay after each failed retry. */
    val backoffMultiplier: Double = 2.0,

    /** Absolute ceiling for the backoff delay (milliseconds). */
    val maxBackoffMs: Long = 60_000,

    // ── Per-action retry budget ───────────────────────────────

    /** Max times an individual action can be retried across sync cycles. */
    val maxRetriesPerAction: Int = 5,

    // ── WorkManager scheduling ────────────────────────────────

    /** Interval for the periodic background sync (minutes). */
    val periodicSyncIntervalMinutes: Long = 15,

    /** Initial backoff for WorkManager retry (milliseconds). */
    val workerInitialBackoffMs: Long = 30_000,

    // ── Worker-level retry ────────────────────────────────────

    /** Max times the SyncWorker itself is retried by WorkManager. */
    val maxWorkerRetries: Int = 5
)

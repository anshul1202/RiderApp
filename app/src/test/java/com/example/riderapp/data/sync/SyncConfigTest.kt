package com.example.riderapp.data.sync

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SyncConfig — verifies backoff calculation logic
 * and default configuration values.
 */
class SyncConfigTest {

    private val config = SyncConfig()

    // ── Default values ───────────────────────────────────────────

    @Test
    fun `default batch size is 50`() {
        assertEquals(50, config.batchSize)
    }

    @Test
    fun `default max retries per batch is 3`() {
        assertEquals(3, config.maxRetriesPerBatch)
    }

    @Test
    fun `default max retries per action is 5`() {
        assertEquals(5, config.maxRetriesPerAction)
    }

    @Test
    fun `default initial backoff is 1 second`() {
        assertEquals(1_000L, config.initialBackoffMs)
    }

    @Test
    fun `default backoff multiplier is 2x`() {
        assertEquals(2.0, config.backoffMultiplier, 0.001)
    }

    @Test
    fun `default max backoff is 60 seconds`() {
        assertEquals(60_000L, config.maxBackoffMs)
    }

    @Test
    fun `default periodic sync interval is 15 minutes`() {
        assertEquals(15L, config.periodicSyncIntervalMinutes)
    }

    // ── Exponential backoff calculation ───────────────────────────

    @Test
    fun `backoff sequence with default config - 1s, 2s, 4s`() {
        var delay = config.initialBackoffMs

        assertEquals(1_000L, delay) // Attempt 1

        delay = (delay * config.backoffMultiplier).toLong()
            .coerceAtMost(config.maxBackoffMs)
        assertEquals(2_000L, delay) // Attempt 2

        delay = (delay * config.backoffMultiplier).toLong()
            .coerceAtMost(config.maxBackoffMs)
        assertEquals(4_000L, delay) // Attempt 3
    }

    @Test
    fun `backoff is capped at maxBackoffMs`() {
        val aggressiveConfig = SyncConfig(
            initialBackoffMs = 30_000,
            backoffMultiplier = 3.0,
            maxBackoffMs = 60_000
        )

        var delay = aggressiveConfig.initialBackoffMs
        assertEquals(30_000L, delay) // Attempt 1

        delay = (delay * aggressiveConfig.backoffMultiplier).toLong()
            .coerceAtMost(aggressiveConfig.maxBackoffMs)
        assertEquals(60_000L, delay) // Attempt 2 — capped at 60s, not 90s

        delay = (delay * aggressiveConfig.backoffMultiplier).toLong()
            .coerceAtMost(aggressiveConfig.maxBackoffMs)
        assertEquals(60_000L, delay) // Attempt 3 — still capped
    }

    @Test
    fun `full backoff sequence with 5 retries stays reasonable`() {
        val config5 = SyncConfig(
            maxRetriesPerBatch = 5,
            initialBackoffMs = 1_000,
            backoffMultiplier = 2.0,
            maxBackoffMs = 60_000
        )

        val delays = mutableListOf<Long>()
        var delay = config5.initialBackoffMs

        repeat(config5.maxRetriesPerBatch) {
            delays.add(delay)
            delay = (delay * config5.backoffMultiplier).toLong()
                .coerceAtMost(config5.maxBackoffMs)
        }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), delays)
        // Total wait = 31 seconds — reasonable for 5 retries
        assertEquals(31_000L, delays.sum())
    }

    // ── Custom configuration ─────────────────────────────────────

    @Test
    fun `custom config overrides defaults`() {
        val custom = SyncConfig(
            batchSize = 100,
            maxRetriesPerBatch = 5,
            initialBackoffMs = 500,
            backoffMultiplier = 1.5,
            maxBackoffMs = 30_000,
            maxRetriesPerAction = 10,
            periodicSyncIntervalMinutes = 30,
            workerInitialBackoffMs = 60_000,
            maxWorkerRetries = 8
        )

        assertEquals(100, custom.batchSize)
        assertEquals(5, custom.maxRetriesPerBatch)
        assertEquals(500L, custom.initialBackoffMs)
        assertEquals(1.5, custom.backoffMultiplier, 0.001)
        assertEquals(30_000L, custom.maxBackoffMs)
        assertEquals(10, custom.maxRetriesPerAction)
        assertEquals(30L, custom.periodicSyncIntervalMinutes)
        assertEquals(60_000L, custom.workerInitialBackoffMs)
        assertEquals(8, custom.maxWorkerRetries)
    }

    // ── Batch count calculation ──────────────────────────────────

    @Test
    fun `1000 records with batch size 50 requires 20 batches`() {
        val totalRecords = 1000
        val batches = (totalRecords + config.batchSize - 1) / config.batchSize
        assertEquals(20, batches)
    }

    @Test
    fun `53 records with batch size 50 requires 2 batches`() {
        val totalRecords = 53
        val batches = (totalRecords + config.batchSize - 1) / config.batchSize
        assertEquals(2, batches)
    }

    @Test
    fun `50 records with batch size 50 requires 1 batch`() {
        val totalRecords = 50
        val batches = (totalRecords + config.batchSize - 1) / config.batchSize
        assertEquals(1, batches)
    }

    @Test
    fun `0 records requires 0 batches`() {
        val totalRecords = 0
        val batches = if (totalRecords == 0) 0
                      else (totalRecords + config.batchSize - 1) / config.batchSize
        assertEquals(0, batches)
    }
}

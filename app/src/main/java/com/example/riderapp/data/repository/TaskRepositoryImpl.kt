package com.example.riderapp.data.repository

import android.util.Log
import com.example.riderapp.data.local.dao.TaskActionDao
import com.example.riderapp.data.local.dao.TaskDao
import com.example.riderapp.data.local.entity.TaskActionEntity
import com.example.riderapp.data.mapper.TaskMapper.toDomain
import com.example.riderapp.data.mapper.TaskMapper.toDto
import com.example.riderapp.data.mapper.TaskMapper.toEntity
import com.example.riderapp.data.remote.api.TaskApi
import com.example.riderapp.data.remote.dto.SyncRequest
import com.example.riderapp.data.sync.SyncConfig
import com.example.riderapp.domain.model.*
import com.example.riderapp.domain.repository.TaskRepository
import com.example.riderapp.monitoring.MonitoringService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val taskActionDao: TaskActionDao,
    private val taskApi: TaskApi,
    private val syncConfig: SyncConfig,
    private val monitoringService: MonitoringService
) : TaskRepository {

    companion object {
        private const val TAG = "TaskRepositoryImpl"
    }

    override fun getTasks(riderId: String, typeFilter: TaskType?, searchQuery: String?): Flow<List<Task>> {
        val query = searchQuery?.trim()
        Log.d(TAG, "getTasks() riderId=$riderId, filter=$typeFilter, search='${query ?: ""}'")
        return when {
            !query.isNullOrBlank() && typeFilter != null ->
                taskDao.searchTasksByType(riderId, typeFilter.name, query)
            !query.isNullOrBlank() ->
                taskDao.searchTasks(riderId, query)
            typeFilter != null ->
                taskDao.getTasksByRiderAndType(riderId, typeFilter.name)
            else ->
                taskDao.getTasksByRider(riderId)
        }.map { entities ->
            Log.d(TAG, "getTasks() emitted ${entities.size} tasks from Room")
            entities.map { it.toDomain() }
        }
    }

    override fun getTaskById(taskId: String): Flow<Task?> {
        Log.d(TAG, "getTaskById() taskId=$taskId")
        return taskDao.getTaskById(taskId).map { entity ->
            val domain = entity?.toDomain()
            Log.d(TAG, "getTaskById() $taskId → status=${domain?.status}, syncStatus=${domain?.syncStatus}")
            domain
        }
    }

    override fun getActionsByTaskId(taskId: String): Flow<List<TaskAction>> {
        return taskActionDao.getActionsByTaskId(taskId).map { entities ->
            Log.d(TAG, "getActionsByTaskId() $taskId → ${entities.size} actions")
            entities.map { it.toDomain() }
        }
    }

    override fun getUnsyncedActionCount(): Flow<Int> {
        return taskActionDao.getUnsyncedActionCount()
    }

    override suspend fun performAction(taskId: String, actionType: ActionType, notes: String?) {
        val now = System.currentTimeMillis()
        val actionId = UUID.randomUUID().toString()

        Log.d(TAG, "performAction() taskId=$taskId, action=${actionType.name}, actionId=$actionId")

        // Create action record (unsynced)
        val actionEntity = TaskActionEntity(
            id = actionId,
            taskId = taskId,
            actionType = actionType.name,
            timestamp = now,
            notes = notes,
            isSynced = false
        )
        taskActionDao.insertAction(actionEntity)
        Log.d(TAG, "performAction() inserted action record (unsynced)")

        // Update task status locally
        val newStatus = ActionType.getResultingStatus(actionType)
        taskDao.updateTaskStatus(
            taskId = taskId,
            status = newStatus.name,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING.name
        )
        Log.d(TAG, "performAction() task $taskId status updated to ${newStatus.name} (PENDING)")

        monitoringService.logEvent("task_action_performed", mapOf(
            "taskId" to taskId,
            "actionType" to actionType.name,
            "offline" to true
        ))
    }

    override suspend fun createTask(task: Task) {
        Log.d(TAG, "createTask() id=${task.id}, type=${task.type}, customer=${task.customerName}")
        val entity = task.toEntity()
        taskDao.insertTask(entity)
        Log.d(TAG, "createTask() inserted into Room (syncStatus=PENDING)")

        monitoringService.logEvent("task_created", mapOf(
            "taskId" to task.id,
            "type" to task.type.name,
            "offline" to true
        ))
    }

    override suspend fun fetchTasksFromServer(riderId: String) {
        try {
            var page = 0
            var totalPages = 1

            // Get IDs of tasks with pending local changes — these must NOT be
            // overwritten by stale server data (server doesn't know about them yet)
            val pendingIds = taskDao.getPendingTaskIds().toSet()

            val pageSize = 50
            while (page < totalPages) {
                val response = taskApi.getTasks(riderId = riderId, page = page, size = pageSize)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        totalPages = body.totalPages
                        val allEntities = body.data.map { it.toEntity() }

                        // Skip tasks that have unsynced local changes
                        val safeEntities = allEntities.filter { it.id !in pendingIds }

                        if (safeEntities.isNotEmpty()) {
                            taskDao.insertTasks(safeEntities)
                        }

                        monitoringService.logEvent("tasks_fetched_page", mapOf(
                            "page" to page,
                            "count" to safeEntities.size,
                            "skipped_pending" to (allEntities.size - safeEntities.size),
                            "totalPages" to totalPages
                        ))

                        // If we got fewer items than requested, we've reached the last page
                        if (body.data.size < pageSize) {
                            break
                        }
                    }
                } else {
                    monitoringService.logError(
                        RuntimeException("Failed to fetch tasks page $page: ${response.code()}"),
                        mapOf("page" to page, "code" to response.code())
                    )
                    break
                }
                page++
            }

            monitoringService.logEvent("tasks_fetch_complete", mapOf(
                "riderId" to riderId,
                "totalPages" to totalPages
            ))
        } catch (e: Exception) {
            monitoringService.logError(e, mapOf("operation" to "fetchTasksFromServer", "riderId" to riderId))
            throw e
        }
    }

    // ── Batched sync with per-batch exponential backoff ──────────────────

    /**
     * Syncs ALL unsynced actions in batches of [SyncConfig.batchSize].
     *
     * Flow:
     *  1. Fetch next batch of unsynced actions from Room
     *  2. Send to API with up to [SyncConfig.maxRetriesPerBatch] retries
     *     using exponential backoff
     *  3. Process response (mark synced / update retry info)
     *  4. Repeat until no more unsynced actions
     *  5. Aggregate results across all batches
     */
    override suspend fun syncActions(): SyncResult {
        var totalSynced = 0
        var totalFailed = 0
        val allErrors = mutableListOf<String>()
        var batchNumber = 0

        Log.i(TAG, "syncActions started — batchSize=${syncConfig.batchSize}, " +
                "maxRetriesPerBatch=${syncConfig.maxRetriesPerBatch}, " +
                "backoff=${syncConfig.initialBackoffMs}ms×${syncConfig.backoffMultiplier}")

        monitoringService.logEvent("sync_all_started", mapOf(
            "batch_size" to syncConfig.batchSize,
            "max_retries_per_batch" to syncConfig.maxRetriesPerBatch
        ))

        while (true) {
            // ── Step 1: Fetch next batch ──
            val batch = taskActionDao.getUnsyncedActionsForSync(
                maxRetries = syncConfig.maxRetriesPerAction,
                batchSize = syncConfig.batchSize
            )

            if (batch.isEmpty()) {
                Log.i(TAG, "No more unsynced actions — batch loop complete")
                break
            }

            batchNumber++
            Log.i(TAG, "Processing batch #$batchNumber (${batch.size} actions)")

            monitoringService.logEvent("sync_batch_started", mapOf(
                "batch" to batchNumber,
                "size" to batch.size
            ))

            // ── Step 2: Send batch with retry + exponential backoff ──
            val batchResult = syncBatchWithRetry(batch, batchNumber)

            totalSynced += batchResult.syncedCount
            totalFailed += batchResult.failedCount
            allErrors.addAll(batchResult.errors)

            Log.i(TAG, "Batch #$batchNumber result: synced=${batchResult.syncedCount}, " +
                    "failed=${batchResult.failedCount}")

            monitoringService.logEvent("sync_batch_completed", mapOf(
                "batch" to batchNumber,
                "synced" to batchResult.syncedCount,
                "failed" to batchResult.failedCount
            ))

            // If an entire batch failed with zero successes, stop processing further batches
            // to avoid hammering a potentially down server
            if (batchResult.syncedCount == 0 && batchResult.failedCount > 0) {
                Log.w(TAG, "Batch #$batchNumber totally failed — stopping batch loop")
                monitoringService.logEvent("sync_batch_loop_stopped", mapOf(
                    "reason" to "batch_total_failure",
                    "batch" to batchNumber
                ))
                break
            }
        }

        // ── Step 3: Aggregate results ──
        monitoringService.logSyncMetric(totalSynced, totalFailed, totalSynced + totalFailed)

        Log.i(TAG, "syncActions complete — total synced=$totalSynced, failed=$totalFailed, " +
                "batches=$batchNumber")

        return when {
            totalSynced == 0 && totalFailed == 0 -> SyncResult.Success(0)
            totalFailed == 0 -> SyncResult.Success(totalSynced)
            totalSynced > 0 -> SyncResult.PartialSuccess(totalSynced, totalFailed, allErrors)
            else -> SyncResult.Failure(allErrors.firstOrNull() ?: "All batches failed")
        }
    }

    // ── Internal: per-batch result ────────────────────────────────────

    private data class BatchSyncResult(
        val syncedCount: Int,
        val failedCount: Int,
        val errors: List<String>
    )

    // ── Internal: retry a single batch with exponential backoff ────────

    private suspend fun syncBatchWithRetry(
        batch: List<TaskActionEntity>,
        batchNumber: Int
    ): BatchSyncResult {
        var attempt = 0
        var backoffMs = syncConfig.initialBackoffMs

        while (true) {
            try {
                return executeBatchSync(batch)
            } catch (e: Exception) {
                attempt++

                if (attempt >= syncConfig.maxRetriesPerBatch) {
                    // Exhausted retries for this batch
                    Log.e(TAG, "Batch #$batchNumber failed after $attempt retries: ${e.message}")

                    monitoringService.logError(e, mapOf(
                        "operation" to "syncBatchWithRetry",
                        "batch" to batchNumber,
                        "attempts" to attempt
                    ))

                    // Mark each action so it counts toward its per-action retry budget
                    batch.forEach { action ->
                        taskActionDao.updateRetryInfo(
                            action.id,
                            "Batch #$batchNumber failed after $attempt retries: ${e.message}"
                        )
                    }

                    return BatchSyncResult(
                        syncedCount = 0,
                        failedCount = batch.size,
                        errors = listOf("Batch #$batchNumber failed after $attempt retries: ${e.message}")
                    )
                }

                // ── Exponential backoff ──
                Log.w(TAG, "Batch #$batchNumber attempt $attempt failed, " +
                        "retrying in ${backoffMs}ms — ${e.message}")

                monitoringService.logEvent("sync_batch_retry", mapOf(
                    "batch" to batchNumber,
                    "attempt" to attempt,
                    "backoff_ms" to backoffMs,
                    "error" to (e.message ?: "Unknown")
                ))

                delay(backoffMs)
                backoffMs = (backoffMs * syncConfig.backoffMultiplier).toLong()
                    .coerceAtMost(syncConfig.maxBackoffMs)
            }
        }
    }

    // ── Internal: execute one batch API call + process response ────────

    private suspend fun executeBatchSync(batch: List<TaskActionEntity>): BatchSyncResult {
        val actionDtos = batch.map { it.toDto() }
        val response = taskApi.syncActions(SyncRequest(actions = actionDtos))

        if (!response.isSuccessful) {
            throw RuntimeException("Sync API returned HTTP ${response.code()}")
        }

        val syncResponse = response.body()
            ?: throw RuntimeException("Sync API returned empty body")

        // The mocker API may return a different shape (e.g. echoed request).
        // If the server returned 200 but no explicit syncedIds, treat ALL
        // submitted actions as synced — the server accepted the payload.
        val syncedIds = syncResponse.syncedIds ?: batch.map { it.id }
        val failedIds = syncResponse.failedIds.orEmpty()
        val errors = syncResponse.errors.orEmpty()

        // Mark synced actions + update task sync status
        syncedIds.forEach { actionId ->
            taskActionDao.markAsSynced(actionId)
            val action = batch.find { it.id == actionId }
            if (action != null) {
                taskDao.updateTaskStatus(
                    taskId = action.taskId,
                    status = taskDao.getTaskByIdSync(action.taskId)?.status ?: return@forEach,
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.SYNCED.name
                )
            }
        }

        // Record per-action failures
        failedIds.forEachIndexed { index, actionId ->
            val error = errors.getOrElse(index) { "Unknown error" }
            taskActionDao.updateRetryInfo(actionId, error)
        }

        return BatchSyncResult(
            syncedCount = syncedIds.size,
            failedCount = failedIds.size,
            errors = errors
        )
    }

    override suspend fun getTaskCount(riderId: String): Int {
        return taskDao.getTaskCount(riderId)
    }
}

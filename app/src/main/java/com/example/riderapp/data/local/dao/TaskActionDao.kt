package com.example.riderapp.data.local.dao

import androidx.room.*
import com.example.riderapp.data.local.entity.TaskActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskActionDao {
    @Query("SELECT * FROM task_actions WHERE taskId = :taskId ORDER BY timestamp ASC")
    fun getActionsByTaskId(taskId: String): Flow<List<TaskActionEntity>>

    @Query("SELECT * FROM task_actions WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedActions(): List<TaskActionEntity>

    @Query("SELECT COUNT(*) FROM task_actions WHERE isSynced = 0")
    fun getUnsyncedActionCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: TaskActionEntity)

    @Query("UPDATE task_actions SET isSynced = 1 WHERE id = :actionId")
    suspend fun markAsSynced(actionId: String)

    @Query("UPDATE task_actions SET retryCount = retryCount + 1, lastError = :error WHERE id = :actionId")
    suspend fun updateRetryInfo(actionId: String, error: String)

    @Query("SELECT * FROM task_actions WHERE isSynced = 0 AND retryCount < :maxRetries ORDER BY timestamp ASC LIMIT :batchSize")
    suspend fun getUnsyncedActionsForSync(maxRetries: Int = 5, batchSize: Int = 50): List<TaskActionEntity>

    @Query("DELETE FROM task_actions WHERE isSynced = 1")
    suspend fun deleteSyncedActions()
}

package com.example.riderapp.data.local.dao

import androidx.room.*
import com.example.riderapp.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE riderId = :riderId ORDER BY updatedAt DESC")
    fun getTasksByRider(riderId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE riderId = :riderId AND type = :type ORDER BY updatedAt DESC")
    fun getTasksByRiderAndType(riderId: String, type: String): Flow<List<TaskEntity>>

    @Query("""
        SELECT * FROM tasks WHERE riderId = :riderId 
        AND (id LIKE '%' || :query || '%' 
             OR customerName LIKE '%' || :query || '%' 
             OR customerPhone LIKE '%' || :query || '%' 
             OR address LIKE '%' || :query || '%' 
             OR description LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
    """)
    fun searchTasks(riderId: String, query: String): Flow<List<TaskEntity>>

    @Query("""
        SELECT * FROM tasks WHERE riderId = :riderId AND type = :type
        AND (id LIKE '%' || :query || '%' 
             OR customerName LIKE '%' || :query || '%' 
             OR customerPhone LIKE '%' || :query || '%' 
             OR address LIKE '%' || :query || '%' 
             OR description LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
    """)
    fun searchTasksByType(riderId: String, type: String, query: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskById(taskId: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskByIdSync(taskId: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt, syncStatus = :syncStatus WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: String, updatedAt: Long, syncStatus: String)

    /** Returns IDs of tasks that have pending (unsynced) local changes */
    @Query("SELECT id FROM tasks WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingTaskIds(): List<String>

    @Query("SELECT COUNT(*) FROM tasks WHERE riderId = :riderId")
    suspend fun getTaskCount(riderId: String): Int

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

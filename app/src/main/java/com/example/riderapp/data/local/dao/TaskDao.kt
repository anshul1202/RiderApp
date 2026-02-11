package com.example.riderapp.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.example.riderapp.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    // ── Flow queries (detail screen, backward compat) ────────

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

    // ── Paging queries (for 4K+ scale) ───────────────────────

    @Query("SELECT * FROM tasks WHERE riderId = :riderId ORDER BY updatedAt DESC")
    fun getTasksByRiderPaged(riderId: String): PagingSource<Int, TaskEntity>

    @Query("SELECT * FROM tasks WHERE riderId = :riderId AND type = :type ORDER BY updatedAt DESC")
    fun getTasksByRiderAndTypePaged(riderId: String, type: String): PagingSource<Int, TaskEntity>

    @Query("""
        SELECT * FROM tasks WHERE riderId = :riderId 
        AND (id LIKE '%' || :query || '%' 
             OR customerName LIKE '%' || :query || '%' 
             OR customerPhone LIKE '%' || :query || '%' 
             OR address LIKE '%' || :query || '%' 
             OR description LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
    """)
    fun searchTasksPaged(riderId: String, query: String): PagingSource<Int, TaskEntity>

    @Query("""
        SELECT * FROM tasks WHERE riderId = :riderId AND type = :type
        AND (id LIKE '%' || :query || '%' 
             OR customerName LIKE '%' || :query || '%' 
             OR customerPhone LIKE '%' || :query || '%' 
             OR address LIKE '%' || :query || '%' 
             OR description LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
    """)
    fun searchTasksByTypePaged(riderId: String, type: String, query: String): PagingSource<Int, TaskEntity>

    // ── Count queries (for UI badge) ─────────────────────────

    @Query("SELECT COUNT(*) FROM tasks WHERE riderId = :riderId")
    fun getTaskCountFlow(riderId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE riderId = :riderId AND type = :type")
    fun getTaskCountByTypeFlow(riderId: String, type: String): Flow<Int>

    // ── Single task ──────────────────────────────────────────

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskById(taskId: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskByIdSync(taskId: String): TaskEntity?

    // ── Writes ───────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt, syncStatus = :syncStatus WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: String, updatedAt: Long, syncStatus: String)

    @Query("SELECT id FROM tasks WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingTaskIds(): List<String>

    @Query("SELECT COUNT(*) FROM tasks WHERE riderId = :riderId")
    suspend fun getTaskCount(riderId: String): Int

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

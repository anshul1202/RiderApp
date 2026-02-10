package com.example.riderapp.domain.repository

import com.example.riderapp.domain.model.*
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getTasks(riderId: String, typeFilter: TaskType? = null, searchQuery: String? = null): Flow<List<Task>>
    fun getTaskById(taskId: String): Flow<Task?>
    fun getActionsByTaskId(taskId: String): Flow<List<TaskAction>>
    fun getUnsyncedActionCount(): Flow<Int>
    suspend fun performAction(taskId: String, actionType: ActionType, notes: String? = null)
    suspend fun createTask(task: Task)
    suspend fun fetchTasksFromServer(riderId: String)
    suspend fun syncActions(): SyncResult
    suspend fun getTaskCount(riderId: String): Int
}

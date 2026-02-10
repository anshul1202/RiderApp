package com.example.riderapp.domain.usecase

import com.example.riderapp.domain.model.Task
import com.example.riderapp.domain.model.TaskAction
import com.example.riderapp.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTaskDetailUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    fun getTask(taskId: String): Flow<Task?> {
        return repository.getTaskById(taskId)
    }

    fun getActions(taskId: String): Flow<List<TaskAction>> {
        return repository.getActionsByTaskId(taskId)
    }
}

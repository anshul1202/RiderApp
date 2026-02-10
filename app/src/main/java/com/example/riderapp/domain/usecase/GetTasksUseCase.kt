package com.example.riderapp.domain.usecase

import com.example.riderapp.domain.model.Task
import com.example.riderapp.domain.model.TaskType
import com.example.riderapp.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTasksUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    operator fun invoke(
        riderId: String,
        typeFilter: TaskType? = null,
        searchQuery: String? = null
    ): Flow<List<Task>> {
        return repository.getTasks(riderId, typeFilter, searchQuery)
    }
}

package com.example.riderapp.domain.usecase

import androidx.paging.PagingData
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

    /** Paged version for 4K+ scale — streams pages of 30 items on demand */
    fun paged(
        riderId: String,
        typeFilter: TaskType? = null,
        searchQuery: String? = null
    ): Flow<PagingData<Task>> {
        return repository.getTasksPaged(riderId, typeFilter, searchQuery)
    }

    /** Reactive task count (separate from paging, for UI badge) */
    fun count(riderId: String, typeFilter: TaskType? = null): Flow<Int> {
        return repository.getTaskCountFlow(riderId, typeFilter)
    }
}

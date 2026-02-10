package com.example.riderapp.domain.usecase

import com.example.riderapp.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FetchTasksUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(riderId: String) {
        repository.fetchTasksFromServer(riderId)
    }

    fun getUnsyncedCount(): Flow<Int> {
        return repository.getUnsyncedActionCount()
    }
}

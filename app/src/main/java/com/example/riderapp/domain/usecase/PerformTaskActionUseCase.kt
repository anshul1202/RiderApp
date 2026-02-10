package com.example.riderapp.domain.usecase

import com.example.riderapp.domain.model.ActionType
import com.example.riderapp.domain.repository.TaskRepository
import javax.inject.Inject

class PerformTaskActionUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(taskId: String, actionType: ActionType, notes: String? = null) {
        repository.performAction(taskId, actionType, notes)
    }
}

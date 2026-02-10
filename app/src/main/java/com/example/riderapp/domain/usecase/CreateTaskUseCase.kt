package com.example.riderapp.domain.usecase

import com.example.riderapp.domain.model.Task
import com.example.riderapp.domain.repository.TaskRepository
import javax.inject.Inject

class CreateTaskUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(task: Task) {
        repository.createTask(task)
    }
}

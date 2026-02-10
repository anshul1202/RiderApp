package com.example.riderapp.presentation.screen.taskdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.riderapp.data.sync.SyncManager
import com.example.riderapp.domain.model.ActionType
import com.example.riderapp.domain.model.Task
import com.example.riderapp.domain.model.TaskAction
import com.example.riderapp.domain.usecase.GetTaskDetailUseCase
import com.example.riderapp.domain.usecase.PerformTaskActionUseCase
import com.example.riderapp.monitoring.MonitoringService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskDetailUiState(
    val task: Task? = null,
    val actions: List<TaskAction> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val availableActions: List<ActionType> = emptyList(),
    val actionInProgress: Boolean = false,
    val actionSuccess: String? = null
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTaskDetailUseCase: GetTaskDetailUseCase,
    private val performTaskActionUseCase: PerformTaskActionUseCase,
    private val syncManager: SyncManager,
    private val monitoringService: MonitoringService
) : ViewModel() {

    private val taskId: String = savedStateHandle.get<String>("taskId") ?: ""

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    init {
        loadTask()
    }

    private fun loadTask() {
        // Observe task
        viewModelScope.launch {
            getTaskDetailUseCase.getTask(taskId).collect { task ->
                _uiState.update { state ->
                    val availableActions = if (task != null) {
                        ActionType.getAvailableActions(task.type, task.status)
                    } else {
                        emptyList()
                    }
                    state.copy(
                        task = task,
                        isLoading = false,
                        availableActions = availableActions
                    )
                }
            }
        }

        // Observe actions
        viewModelScope.launch {
            getTaskDetailUseCase.getActions(taskId).collect { actions ->
                _uiState.update { it.copy(actions = actions) }
            }
        }
    }

    fun performAction(actionType: ActionType, notes: String? = null) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(actionInProgress = true, error = null) }
                performTaskActionUseCase(taskId, actionType, notes)
                _uiState.update { it.copy(
                    actionInProgress = false,
                    actionSuccess = "${actionType.displayName} completed"
                )}
                syncManager.triggerImmediateSync()
                monitoringService.logEvent("action_performed", mapOf(
                    "taskId" to taskId,
                    "action" to actionType.name
                ))
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    actionInProgress = false,
                    error = "Failed: ${e.message}"
                )}
                monitoringService.logError(e, mapOf(
                    "operation" to "performAction",
                    "taskId" to taskId,
                    "action" to actionType.name
                ))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(actionSuccess = null) }
    }
}

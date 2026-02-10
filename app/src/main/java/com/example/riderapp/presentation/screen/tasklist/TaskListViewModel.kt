package com.example.riderapp.presentation.screen.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.riderapp.data.sync.SyncManager
import com.example.riderapp.domain.model.SyncStatus
import com.example.riderapp.domain.model.Task
import com.example.riderapp.domain.model.TaskStatus
import com.example.riderapp.domain.model.TaskType
import com.example.riderapp.domain.usecase.CreateTaskUseCase
import com.example.riderapp.domain.usecase.FetchTasksUseCase
import com.example.riderapp.domain.usecase.GetTasksUseCase
import com.example.riderapp.monitoring.MonitoringService
import com.example.riderapp.util.Constants
import com.example.riderapp.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TaskListUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val typeFilter: TaskType? = null,
    val searchQuery: String = "",
    val unsyncedCount: Int = 0,
    val isOnline: Boolean = true,
    val isInitialDataLoaded: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val getTasksUseCase: GetTasksUseCase,
    private val fetchTasksUseCase: FetchTasksUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val syncManager: SyncManager,
    private val networkMonitor: NetworkMonitor,
    private val monitoringService: MonitoringService
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    private val _typeFilter = MutableStateFlow<TaskType?>(null)
    private val _searchQuery = MutableStateFlow("")

    /** Prevents concurrent loadInitialData calls */
    private var isLoadingData = false

    init {
        // Observe network status
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
                if (online && !_uiState.value.isInitialDataLoaded) {
                    loadInitialData()
                }
            }
        }

        // Observe unsynced count
        viewModelScope.launch {
            fetchTasksUseCase.getUnsyncedCount().collect { count ->
                _uiState.update { it.copy(unsyncedCount = count) }
            }
        }

        // Observe tasks with combined filter + search (debounce search by 300ms)
        viewModelScope.launch {
            combine(
                _typeFilter,
                _searchQuery.debounce(300)
            ) { filter, query ->
                Pair(filter, query)
            }.flatMapLatest { (filter, query) ->
                getTasksUseCase(
                    riderId = Constants.RIDER_ID,
                    typeFilter = filter,
                    searchQuery = query.ifBlank { null }
                )
            }.collect { tasks ->
                _uiState.update {
                    // Only clear loading if we actually have data or initial load is done
                    val shouldStopLoading = tasks.isNotEmpty() || it.isInitialDataLoaded
                    it.copy(
                        tasks = tasks,
                        isLoading = if (shouldStopLoading) false else it.isLoading
                    )
                }
            }
        }

        // Schedule periodic sync
        syncManager.schedulePeriodicSync()
    }

    private fun loadInitialData() {
        if (isLoadingData) return  // Prevent duplicate concurrent calls
        isLoadingData = true

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                fetchTasksUseCase(Constants.RIDER_ID)
                _uiState.update { it.copy(isInitialDataLoaded = true, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Failed to load tasks: ${e.message}",
                    isLoading = false
                )}
                monitoringService.logError(e, mapOf("operation" to "loadInitialData"))
            } finally {
                isLoadingData = false
            }
        }
    }

    fun setTypeFilter(filter: TaskType?) {
        _typeFilter.value = filter
        _uiState.update { it.copy(typeFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun refreshTasks() {
        loadInitialData()
    }

    fun triggerSync() {
        syncManager.triggerImmediateSync()
        monitoringService.logEvent("manual_sync_triggered")
    }

    fun createTask(
        customerName: String,
        customerPhone: String,
        address: String,
        description: String
    ) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val task = Task(
                    id = "LOCAL-${UUID.randomUUID().toString().take(8).uppercase()}",
                    type = TaskType.PICKUP,
                    status = TaskStatus.ASSIGNED,
                    riderId = Constants.RIDER_ID,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    address = address,
                    description = description,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING
                )
                createTaskUseCase(task)
                syncManager.triggerImmediateSync()
                monitoringService.logEvent("task_created_locally", mapOf("taskId" to task.id))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to create task: ${e.message}") }
                monitoringService.logError(e, mapOf("operation" to "createTask"))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

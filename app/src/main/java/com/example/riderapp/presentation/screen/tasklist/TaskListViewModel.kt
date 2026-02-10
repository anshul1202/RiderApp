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
import android.util.Log
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

    companion object {
        private const val TAG = "TaskListVM"
    }

    init {
        Log.d(TAG, "init — ViewModel created")

        // Observe network status
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                Log.d(TAG, "network status changed: online=$online")
                _uiState.update { it.copy(isOnline = online) }
                if (online && !_uiState.value.isInitialDataLoaded) {
                    Log.d(TAG, "online + not loaded → triggering loadInitialData()")
                    loadInitialData()
                }
            }
        }

        // Observe unsynced count
        viewModelScope.launch {
            fetchTasksUseCase.getUnsyncedCount().collect { count ->
                Log.d(TAG, "unsynced action count: $count")
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
                Log.d(TAG, "query changed → filter=$filter, search='${query.ifBlank { "(none)" }}'")
                getTasksUseCase(
                    riderId = Constants.RIDER_ID,
                    typeFilter = filter,
                    searchQuery = query.ifBlank { null }
                )
            }.collect { tasks ->
                Log.d(TAG, "Room emitted ${tasks.size} tasks (isInitialDataLoaded=${_uiState.value.isInitialDataLoaded})")
                _uiState.update {
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
        Log.d(TAG, "init — periodic sync scheduled")
    }

    private fun loadInitialData() {
        if (isLoadingData) {
            Log.d(TAG, "loadInitialData() skipped — already loading")
            return
        }
        isLoadingData = true
        Log.d(TAG, "loadInitialData() started — fetching from server")

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                fetchTasksUseCase(Constants.RIDER_ID)
                Log.d(TAG, "loadInitialData() success — data loaded")
                _uiState.update { it.copy(isInitialDataLoaded = true, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "loadInitialData() failed: ${e.message}", e)
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
        Log.d(TAG, "setTypeFilter($filter)")
        _typeFilter.value = filter
        _uiState.update { it.copy(typeFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        Log.d(TAG, "setSearchQuery('$query')")
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        Log.d(TAG, "clearSearch()")
        _searchQuery.value = ""
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun refreshTasks() {
        Log.d(TAG, "refreshTasks() — manual refresh")
        loadInitialData()
    }

    fun triggerSync() {
        Log.d(TAG, "triggerSync() — manual sync")
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
                Log.d(TAG, "createTask() id=${task.id}, customer=$customerName")
                createTaskUseCase(task)
                Log.d(TAG, "createTask() success — triggering sync")
                syncManager.triggerImmediateSync()
                monitoringService.logEvent("task_created_locally", mapOf("taskId" to task.id))
            } catch (e: Exception) {
                Log.e(TAG, "createTask() failed: ${e.message}", e)
                _uiState.update { it.copy(error = "Failed to create task: ${e.message}") }
                monitoringService.logError(e, mapOf("operation" to "createTask"))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

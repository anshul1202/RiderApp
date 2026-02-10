package com.example.riderapp.presentation.screen.tasklist

import com.example.riderapp.data.sync.SyncManager
import com.example.riderapp.domain.model.*
import com.example.riderapp.domain.usecase.CreateTaskUseCase
import com.example.riderapp.domain.usecase.FetchTasksUseCase
import com.example.riderapp.domain.usecase.GetTasksUseCase
import com.example.riderapp.monitoring.MonitoringService
import com.example.riderapp.util.NetworkMonitor
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    @MockK private lateinit var getTasksUseCase: GetTasksUseCase
    @MockK private lateinit var fetchTasksUseCase: FetchTasksUseCase
    @MockK private lateinit var createTaskUseCase: CreateTaskUseCase
    @MockK(relaxed = true) private lateinit var syncManager: SyncManager
    @MockK private lateinit var networkMonitor: NetworkMonitor
    @MockK(relaxed = true) private lateinit var monitoringService: MonitoringService

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TaskListViewModel {
        // Default stubs for init block flows
        every { networkMonitor.isOnline } returns flowOf(true)
        every { fetchTasksUseCase.getUnsyncedCount() } returns flowOf(0)
        every { getTasksUseCase(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { fetchTasksUseCase(any()) } just Runs

        return TaskListViewModel(
            getTasksUseCase, fetchTasksUseCase, createTaskUseCase,
            syncManager, networkMonitor, monitoringService
        )
    }

    // ── Initial state ────────────────────────────────────────────

    @Test
    fun `initial state has loading true`() {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `initial state has no type filter`() {
        val viewModel = createViewModel()
        assertNull(viewModel.uiState.value.typeFilter)
    }

    @Test
    fun `initial state has empty search query`() {
        val viewModel = createViewModel()
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    // ── Network status ───────────────────────────────────────────

    @Test
    fun `online status is reflected in ui state`() = runTest {
        every { networkMonitor.isOnline } returns flowOf(true)
        every { fetchTasksUseCase.getUnsyncedCount() } returns flowOf(0)
        every { getTasksUseCase(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { fetchTasksUseCase(any()) } just Runs

        val viewModel = TaskListViewModel(
            getTasksUseCase, fetchTasksUseCase, createTaskUseCase,
            syncManager, networkMonitor, monitoringService
        )

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isOnline)
    }

    @Test
    fun `offline status is reflected in ui state`() = runTest {
        every { networkMonitor.isOnline } returns flowOf(false)
        every { fetchTasksUseCase.getUnsyncedCount() } returns flowOf(3)
        every { getTasksUseCase(any(), any(), any()) } returns flowOf(emptyList())

        val viewModel = TaskListViewModel(
            getTasksUseCase, fetchTasksUseCase, createTaskUseCase,
            syncManager, networkMonitor, monitoringService
        )

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOnline)
    }

    // ── Unsynced count ───────────────────────────────────────────

    @Test
    fun `unsynced count from use case is reflected in state`() = runTest {
        every { networkMonitor.isOnline } returns flowOf(true)
        every { fetchTasksUseCase.getUnsyncedCount() } returns flowOf(5)
        every { getTasksUseCase(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { fetchTasksUseCase(any()) } just Runs

        val viewModel = TaskListViewModel(
            getTasksUseCase, fetchTasksUseCase, createTaskUseCase,
            syncManager, networkMonitor, monitoringService
        )

        advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.unsyncedCount)
    }

    // ── Type filter ──────────────────────────────────────────────

    @Test
    fun `setTypeFilter updates state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setTypeFilter(TaskType.PICKUP)
        assertEquals(TaskType.PICKUP, viewModel.uiState.value.typeFilter)

        viewModel.setTypeFilter(null)
        assertNull(viewModel.uiState.value.typeFilter)
    }

    // ── Search ───────────────────────────────────────────────────

    @Test
    fun `setSearchQuery updates state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSearchQuery("Vivaan")
        assertEquals("Vivaan", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `clearSearch resets query to empty`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSearchQuery("test")
        viewModel.clearSearch()
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    // ── Task creation ────────────────────────────────────────────

    @Test
    fun `createTask calls use case and triggers sync`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { createTaskUseCase(any()) } just Runs

        viewModel.createTask("John", "+91000", "123 Main", "Fragile")
        advanceUntilIdle()

        coVerify { createTaskUseCase(match { task ->
            task.customerName == "John" &&
            task.address == "123 Main" &&
            task.type == TaskType.PICKUP &&
            task.status == TaskStatus.ASSIGNED &&
            task.syncStatus == SyncStatus.PENDING
        })}
        verify { syncManager.triggerImmediateSync() }
    }

    @Test
    fun `createTask sets error on failure`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { createTaskUseCase(any()) } throws RuntimeException("DB error")

        viewModel.createTask("John", "+91000", "123 Main", "Fragile")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("DB error"))
    }

    // ── Trigger sync ─────────────────────────────────────────────

    @Test
    fun `triggerSync calls syncManager`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.triggerSync()

        verify { syncManager.triggerImmediateSync() }
    }

    // ── Error handling ───────────────────────────────────────────

    @Test
    fun `clearError resets error to null`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { createTaskUseCase(any()) } throws RuntimeException("error")
        viewModel.createTask("A", "B", "C", "D")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    // ── Periodic sync scheduled on init ──────────────────────────

    @Test
    fun `schedulePeriodicSync is called on init`() = runTest {
        createViewModel()
        advanceUntilIdle()

        verify { syncManager.schedulePeriodicSync() }
    }
}

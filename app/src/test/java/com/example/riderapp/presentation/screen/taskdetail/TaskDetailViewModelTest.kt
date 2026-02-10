package com.example.riderapp.presentation.screen.taskdetail

import androidx.lifecycle.SavedStateHandle
import com.example.riderapp.data.sync.SyncManager
import com.example.riderapp.domain.model.*
import com.example.riderapp.domain.usecase.GetTaskDetailUseCase
import com.example.riderapp.domain.usecase.PerformTaskActionUseCase
import com.example.riderapp.monitoring.MonitoringService
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
class TaskDetailViewModelTest {

    @MockK private lateinit var getTaskDetailUseCase: GetTaskDetailUseCase
    @MockK private lateinit var performTaskActionUseCase: PerformTaskActionUseCase
    @MockK(relaxed = true) private lateinit var syncManager: SyncManager
    @MockK(relaxed = true) private lateinit var monitoringService: MonitoringService

    private val testDispatcher = StandardTestDispatcher()

    private val sampleTask = Task(
        id = "TASK-0001",
        type = TaskType.DROP,
        status = TaskStatus.ASSIGNED,
        riderId = "RIDER-001",
        customerName = "Vivaan Verma",
        customerPhone = "+919000000001",
        address = "14, Park Street, HSR Layout, Pune",
        description = "Express delivery",
        createdAt = 1000L,
        updatedAt = 2000L,
        syncStatus = SyncStatus.SYNCED
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(taskId: String = "TASK-0001"): TaskDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("taskId" to taskId))

        every { getTaskDetailUseCase.getTask(taskId) } returns flowOf(sampleTask)
        every { getTaskDetailUseCase.getActions(taskId) } returns flowOf(emptyList())

        return TaskDetailViewModel(
            savedStateHandle, getTaskDetailUseCase,
            performTaskActionUseCase, syncManager, monitoringService
        )
    }

    // ── Loading task ─────────────────────────────────────────────

    @Test
    fun `loads task on init`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.task)
        assertEquals("TASK-0001", state.task!!.id)
        assertEquals("Vivaan Verma", state.task!!.customerName)
        assertFalse(state.isLoading)
    }

    @Test
    fun `available actions computed from task state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // DROP + ASSIGNED → only REACH is available
        assertEquals(listOf(ActionType.REACH), state.availableActions)
    }

    @Test
    fun `loads action history on init`() = runTest {
        val actions = listOf(
            TaskAction("a1", "TASK-0001", ActionType.REACH, 3000L, isSynced = true)
        )
        val savedStateHandle = SavedStateHandle(mapOf("taskId" to "TASK-0001"))
        every { getTaskDetailUseCase.getTask("TASK-0001") } returns flowOf(
            sampleTask.copy(status = TaskStatus.REACHED)
        )
        every { getTaskDetailUseCase.getActions("TASK-0001") } returns flowOf(actions)

        val viewModel = TaskDetailViewModel(
            savedStateHandle, getTaskDetailUseCase,
            performTaskActionUseCase, syncManager, monitoringService
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.actions.size)
        assertEquals(ActionType.REACH, viewModel.uiState.value.actions[0].actionType)
    }

    // ── Performing actions ───────────────────────────────────────

    @Test
    fun `performAction calls use case and triggers sync`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { performTaskActionUseCase(any(), any(), any()) } just Runs

        viewModel.performAction(ActionType.REACH)
        advanceUntilIdle()

        coVerify { performTaskActionUseCase("TASK-0001", ActionType.REACH, null) }
        verify { syncManager.triggerImmediateSync() }
    }

    @Test
    fun `performAction with notes passes notes to use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { performTaskActionUseCase(any(), any(), any()) } just Runs

        viewModel.performAction(ActionType.FAIL_DELIVERY, "Customer not home")
        advanceUntilIdle()

        coVerify {
            performTaskActionUseCase("TASK-0001", ActionType.FAIL_DELIVERY, "Customer not home")
        }
    }

    @Test
    fun `performAction sets success message`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { performTaskActionUseCase(any(), any(), any()) } just Runs

        viewModel.performAction(ActionType.REACH)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.actionSuccess)
        assertTrue(viewModel.uiState.value.actionSuccess!!.contains("Reach Location"))
    }

    @Test
    fun `performAction sets error on failure`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { performTaskActionUseCase(any(), any(), any()) } throws
                RuntimeException("Database locked")

        viewModel.performAction(ActionType.REACH)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("Database locked"))
        assertFalse(viewModel.uiState.value.actionInProgress)
    }

    // ── Clear messages ───────────────────────────────────────────

    @Test
    fun `clearError resets error`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { performTaskActionUseCase(any(), any(), any()) } throws RuntimeException("err")
        viewModel.performAction(ActionType.REACH)
        advanceUntilIdle()

        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `clearSuccess resets success message`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { performTaskActionUseCase(any(), any(), any()) } just Runs
        viewModel.performAction(ActionType.REACH)
        advanceUntilIdle()

        viewModel.clearSuccess()
        assertNull(viewModel.uiState.value.actionSuccess)
    }
}

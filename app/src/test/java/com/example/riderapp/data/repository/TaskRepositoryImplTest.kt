package com.example.riderapp.data.repository

import com.example.riderapp.data.local.dao.TaskActionDao
import com.example.riderapp.data.local.dao.TaskDao
import com.example.riderapp.data.local.entity.TaskActionEntity
import com.example.riderapp.data.local.entity.TaskEntity
import com.example.riderapp.data.remote.api.TaskApi
import com.example.riderapp.data.remote.dto.*
import com.example.riderapp.data.sync.SyncConfig
import com.example.riderapp.domain.model.*
import com.example.riderapp.monitoring.MonitoringService
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRepositoryImplTest {

    @MockK private lateinit var taskDao: TaskDao
    @MockK private lateinit var taskActionDao: TaskActionDao
    @MockK private lateinit var taskApi: TaskApi
    @MockK(relaxed = true) private lateinit var monitoringService: MonitoringService
    @MockK(relaxed = true) private lateinit var prefs: android.content.SharedPreferences
    @MockK(relaxed = true) private lateinit var prefsEditor: android.content.SharedPreferences.Editor

    private lateinit var repository: TaskRepositoryImpl

    private val syncConfig = SyncConfig(
        batchSize = 2,              // Small batch for testing
        maxRetriesPerBatch = 2,
        initialBackoffMs = 10,      // Fast retries for tests
        backoffMultiplier = 2.0,
        maxBackoffMs = 100,
        maxRetriesPerAction = 3
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefs.getLong(any(), any()) } returns 0L
        repository = TaskRepositoryImpl(taskDao, taskActionDao, taskApi, syncConfig, monitoringService, prefs)
    }

    // ── getTasks ─────────────────────────────────────────────────

    @Test
    fun `getTasks without filter returns all tasks from DAO`() = runTest {
        val entities = listOf(
            createTaskEntity("TASK-0001", "DROP"),
            createTaskEntity("TASK-0002", "PICKUP")
        )
        every { taskDao.getTasksByRider("RIDER-001") } returns flowOf(entities)

        val tasks = repository.getTasks("RIDER-001").first()

        assertEquals(2, tasks.size)
        assertEquals("TASK-0001", tasks[0].id)
        assertEquals("TASK-0002", tasks[1].id)
    }

    @Test
    fun `getTasks with PICKUP filter queries by type`() = runTest {
        val entities = listOf(createTaskEntity("TASK-0002", "PICKUP"))
        every { taskDao.getTasksByRiderAndType("RIDER-001", "PICKUP") } returns flowOf(entities)

        val tasks = repository.getTasks("RIDER-001", TaskType.PICKUP).first()

        assertEquals(1, tasks.size)
        assertEquals(TaskType.PICKUP, tasks[0].type)
    }

    @Test
    fun `getTasks with search query calls searchTasks`() = runTest {
        val entities = listOf(createTaskEntity("TASK-0001", "DROP"))
        every { taskDao.searchTasks("RIDER-001", "Vivaan") } returns flowOf(entities)

        val tasks = repository.getTasks("RIDER-001", searchQuery = "Vivaan").first()

        assertEquals(1, tasks.size)
        verify { taskDao.searchTasks("RIDER-001", "Vivaan") }
    }

    @Test
    fun `getTasks with search and type filter calls searchTasksByType`() = runTest {
        val entities = listOf(createTaskEntity("TASK-0001", "DROP"))
        every { taskDao.searchTasksByType("RIDER-001", "DROP", "Park") } returns flowOf(entities)

        val tasks = repository.getTasks("RIDER-001", TaskType.DROP, "Park").first()

        assertEquals(1, tasks.size)
        verify { taskDao.searchTasksByType("RIDER-001", "DROP", "Park") }
    }

    // ── performAction ────────────────────────────────────────────

    @Test
    fun `performAction inserts action entity and updates task status`() = runTest {
        coEvery { taskActionDao.insertAction(any()) } just Runs
        coEvery { taskDao.updateTaskStatus(any(), any(), any(), any()) } just Runs

        repository.performAction("TASK-0001", ActionType.REACH, "On my way")

        coVerify {
            taskActionDao.insertAction(match { action ->
                action.taskId == "TASK-0001" &&
                action.actionType == "REACH" &&
                action.notes == "On my way" &&
                !action.isSynced
            })
        }
        coVerify {
            taskDao.updateTaskStatus(
                taskId = "TASK-0001",
                status = "REACHED",
                updatedAt = any(),
                syncStatus = "PENDING"
            )
        }
    }

    // ── createTask ───────────────────────────────────────────────

    @Test
    fun `createTask inserts task entity into DAO`() = runTest {
        coEvery { taskDao.insertTask(any()) } just Runs

        val task = Task(
            id = "LOCAL-TEST1234",
            type = TaskType.PICKUP,
            status = TaskStatus.ASSIGNED,
            riderId = "RIDER-001",
            customerName = "Test",
            customerPhone = "+91000",
            address = "Test Addr",
            description = "Test Desc",
            createdAt = 1000L,
            updatedAt = 1000L,
            syncStatus = SyncStatus.PENDING
        )

        repository.createTask(task)

        coVerify {
            taskDao.insertTask(match { entity ->
                entity.id == "LOCAL-TEST1234" &&
                entity.type == "PICKUP" &&
                entity.syncStatus == "PENDING"
            })
        }
    }

    // ── syncActions — batched processing ─────────────────────────

    @Test
    fun `syncActions returns Success(0) when no unsynced actions`() = runTest {
        coEvery { taskActionDao.getUnsyncedActionsForSync(any(), any()) } returns emptyList()

        val result = repository.syncActions()

        assertTrue(result is SyncResult.Success)
        assertEquals(0, (result as SyncResult.Success).syncedCount)
    }

    @Test
    fun `syncActions processes single batch successfully`() = runTest {
        val actions = listOf(
            createActionEntity("act-1", "TASK-0001"),
            createActionEntity("act-2", "TASK-0002")
        )

        // First call returns actions, second call returns empty (loop ends)
        coEvery { taskActionDao.getUnsyncedActionsForSync(any(), any()) } returnsMany listOf(
            actions, emptyList()
        )
        coEvery { taskApi.syncActions(any()) } returns Response.success(
            SyncResponse(
                syncedIds = listOf("act-1", "act-2"),
                failedIds = emptyList(),
                errors = emptyList()
            )
        )
        coEvery { taskActionDao.markAsSynced(any()) } just Runs
        coEvery { taskDao.getTaskByIdSync(any()) } returns createTaskEntity("TASK-0001", "DROP")
        coEvery { taskDao.updateTaskStatus(any(), any(), any(), any()) } just Runs

        val result = repository.syncActions()

        assertTrue(result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).syncedCount)
        coVerify(exactly = 2) { taskActionDao.markAsSynced(any()) }
    }

    @Test
    fun `syncActions processes multiple batches with batchSize 2`() = runTest {
        val batch1 = listOf(
            createActionEntity("act-1", "TASK-0001"),
            createActionEntity("act-2", "TASK-0002")
        )
        val batch2 = listOf(
            createActionEntity("act-3", "TASK-0003")
        )

        coEvery { taskActionDao.getUnsyncedActionsForSync(any(), any()) } returnsMany listOf(
            batch1, batch2, emptyList()
        )
        coEvery { taskApi.syncActions(any()) } returns Response.success(
            SyncResponse(syncedIds = null, failedIds = null, errors = null)
        )
        coEvery { taskActionDao.markAsSynced(any()) } just Runs
        coEvery { taskDao.getTaskByIdSync(any()) } returns createTaskEntity("TASK-0001", "DROP")
        coEvery { taskDao.updateTaskStatus(any(), any(), any(), any()) } just Runs

        val result = repository.syncActions()

        assertTrue(result is SyncResult.Success)
        // With null syncedIds, all batch items treated as synced (batch1=2 + batch2=1)
        assertEquals(3, (result as SyncResult.Success).syncedCount)
    }

    @Test
    fun `syncActions returns PartialSuccess when some actions fail`() = runTest {
        val actions = listOf(
            createActionEntity("act-1", "TASK-0001"),
            createActionEntity("act-2", "TASK-0002")
        )

        coEvery { taskActionDao.getUnsyncedActionsForSync(any(), any()) } returnsMany listOf(
            actions, emptyList()
        )
        coEvery { taskApi.syncActions(any()) } returns Response.success(
            SyncResponse(
                syncedIds = listOf("act-1"),
                failedIds = listOf("act-2"),
                errors = listOf("Task cancelled")
            )
        )
        coEvery { taskActionDao.markAsSynced(any()) } just Runs
        coEvery { taskActionDao.updateRetryInfo(any(), any()) } just Runs
        coEvery { taskDao.getTaskByIdSync(any()) } returns createTaskEntity("TASK-0001", "DROP")
        coEvery { taskDao.updateTaskStatus(any(), any(), any(), any()) } just Runs

        val result = repository.syncActions()

        assertTrue(result is SyncResult.PartialSuccess)
        val partial = result as SyncResult.PartialSuccess
        assertEquals(1, partial.syncedCount)
        assertEquals(1, partial.failedCount)
        assertEquals("Task cancelled", partial.errors.first())
    }

    @Test
    fun `syncActions stops batch loop when batch totally fails`() = runTest {
        val batch1 = listOf(createActionEntity("act-1", "TASK-0001"))

        coEvery { taskActionDao.getUnsyncedActionsForSync(any(), any()) } returns batch1
        // API always throws — simulates server down
        coEvery { taskApi.syncActions(any()) } throws RuntimeException("Server error")
        coEvery { taskActionDao.updateRetryInfo(any(), any()) } just Runs

        val result = repository.syncActions()

        assertTrue(result is SyncResult.Failure)
        // Should have retried maxRetriesPerBatch (2) times then stopped
        coVerify(exactly = 2) { taskApi.syncActions(any()) }
    }

    // ── syncActions — null-safe response handling ────────────────

    @Test
    fun `syncActions handles null syncedIds by treating all as synced`() = runTest {
        val actions = listOf(createActionEntity("act-1", "TASK-0001"))

        coEvery { taskActionDao.getUnsyncedActionsForSync(any(), any()) } returnsMany listOf(
            actions, emptyList()
        )
        // Mocker API returns different shape — syncedIds is null
        coEvery { taskApi.syncActions(any()) } returns Response.success(
            SyncResponse(syncedIds = null, failedIds = null, errors = null)
        )
        coEvery { taskActionDao.markAsSynced(any()) } just Runs
        coEvery { taskDao.getTaskByIdSync(any()) } returns createTaskEntity("TASK-0001", "DROP")
        coEvery { taskDao.updateTaskStatus(any(), any(), any(), any()) } just Runs

        val result = repository.syncActions()

        assertTrue(result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).syncedCount)
        coVerify { taskActionDao.markAsSynced("act-1") }
    }

    // ── fetchTasksFromServer ─────────────────────────────────────

    @Test
    fun `fetchTasksFromServer inserts paginated tasks into Room`() = runTest {
        val page0 = PaginatedResponse(
            data = listOf(
                TaskDto("T-1", "DROP", "ASSIGNED", "R1", "A", "", "", "", 0, 0),
                TaskDto("T-2", "PICKUP", "ASSIGNED", "R1", "B", "", "", "", 0, 0)
            ),
            page = 0, size = 50, totalPages = 1, totalItems = 2
        )

        coEvery { taskDao.getPendingTaskIds() } returns emptyList()
        coEvery { taskApi.getTasks(any(), any(), any()) } returns Response.success(page0)
        coEvery { taskDao.insertTasks(any()) } just Runs

        repository.fetchTasksFromServer("RIDER-001")

        coVerify { taskDao.insertTasks(match { it.size == 2 }) }
    }

    @Test
    fun `fetchTasksFromServer skips tasks with pending local changes`() = runTest {
        val page0 = PaginatedResponse(
            data = listOf(
                TaskDto("T-1", "DROP", "ASSIGNED", "R1", "A", "", "", "", 0, 0),
                TaskDto("T-2", "PICKUP", "ASSIGNED", "R1", "B", "", "", "", 0, 0)
            ),
            page = 0, size = 50, totalPages = 1, totalItems = 2
        )

        // T-1 has pending local changes — should NOT be overwritten
        coEvery { taskDao.getPendingTaskIds() } returns listOf("T-1")
        coEvery { taskApi.getTasks(any(), any(), any()) } returns Response.success(page0)
        coEvery { taskDao.insertTasks(any()) } just Runs

        repository.fetchTasksFromServer("RIDER-001")

        // Only T-2 should be inserted (T-1 skipped)
        coVerify { taskDao.insertTasks(match { it.size == 1 && it[0].id == "T-2" }) }
    }

    @Test
    fun `fetchTasksFromServer stops when data size less than page size`() = runTest {
        val page0 = PaginatedResponse(
            data = listOf(
                TaskDto("T-1", "DROP", "ASSIGNED", "R1", "A", "", "", "", 0, 0)
            ),
            page = 0, size = 50, totalPages = 20, totalItems = 1000
        )

        coEvery { taskDao.getPendingTaskIds() } returns emptyList()
        coEvery { taskApi.getTasks(any(), any(), any()) } returns Response.success(page0)
        coEvery { taskDao.insertTasks(any()) } just Runs

        repository.fetchTasksFromServer("RIDER-001")

        // Only called once because data.size (1) < pageSize (50)
        coVerify(exactly = 1) { taskApi.getTasks(any(), any(), any()) }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun createTaskEntity(id: String, type: String) = TaskEntity(
        id = id, type = type, status = "ASSIGNED", riderId = "RIDER-001",
        customerName = "Test", customerPhone = "+91000",
        address = "Test Addr", description = "Test",
        createdAt = 1000L, updatedAt = 2000L, syncStatus = "SYNCED"
    )

    private fun createActionEntity(id: String, taskId: String) = TaskActionEntity(
        id = id, taskId = taskId, actionType = "REACH",
        timestamp = System.currentTimeMillis(), isSynced = false
    )
}

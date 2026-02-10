package com.example.riderapp.data.mapper

import com.example.riderapp.data.local.entity.TaskActionEntity
import com.example.riderapp.data.local.entity.TaskEntity
import com.example.riderapp.data.mapper.TaskMapper.toDomain
import com.example.riderapp.data.mapper.TaskMapper.toDto
import com.example.riderapp.data.mapper.TaskMapper.toEntity
import com.example.riderapp.data.remote.dto.TaskDto
import com.example.riderapp.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for TaskMapper — ensures correct mapping between
 * Entity, Domain, and DTO layers.
 */
class TaskMapperTest {

    // ── TaskEntity -> Task (Domain) ──────────────────────────────

    @Test
    fun `TaskEntity maps to Task domain model correctly`() {
        val entity = TaskEntity(
            id = "TASK-0001",
            type = "DROP",
            status = "ASSIGNED",
            riderId = "RIDER-001",
            customerName = "John Doe",
            customerPhone = "+919000000001",
            address = "123 Main St",
            description = "Fragile items",
            createdAt = 1000L,
            updatedAt = 2000L,
            syncStatus = "SYNCED"
        )

        val domain = entity.toDomain()

        assertEquals("TASK-0001", domain.id)
        assertEquals(TaskType.DROP, domain.type)
        assertEquals(TaskStatus.ASSIGNED, domain.status)
        assertEquals("RIDER-001", domain.riderId)
        assertEquals("John Doe", domain.customerName)
        assertEquals("+919000000001", domain.customerPhone)
        assertEquals("123 Main St", domain.address)
        assertEquals("Fragile items", domain.description)
        assertEquals(1000L, domain.createdAt)
        assertEquals(2000L, domain.updatedAt)
        assertEquals(SyncStatus.SYNCED, domain.syncStatus)
    }

    @Test
    fun `TaskEntity with PENDING sync status maps correctly`() {
        val entity = TaskEntity(
            id = "TASK-0002", type = "PICKUP", status = "REACHED",
            riderId = "R1", customerName = "A", customerPhone = "",
            address = "", description = "", createdAt = 0L, updatedAt = 0L,
            syncStatus = "PENDING"
        )

        assertEquals(SyncStatus.PENDING, entity.toDomain().syncStatus)
    }

    // ── Task (Domain) -> TaskEntity ──────────────────────────────

    @Test
    fun `Task domain model maps to TaskEntity correctly`() {
        val task = Task(
            id = "TASK-0001",
            type = TaskType.PICKUP,
            status = TaskStatus.PICKED_UP,
            riderId = "RIDER-001",
            customerName = "Jane Doe",
            customerPhone = "+919000000002",
            address = "456 Oak Ave",
            description = "Express",
            createdAt = 3000L,
            updatedAt = 4000L,
            syncStatus = SyncStatus.PENDING
        )

        val entity = task.toEntity()

        assertEquals("TASK-0001", entity.id)
        assertEquals("PICKUP", entity.type)
        assertEquals("PICKED_UP", entity.status)
        assertEquals("RIDER-001", entity.riderId)
        assertEquals("PENDING", entity.syncStatus)
    }

    // ── TaskDto -> TaskEntity ────────────────────────────────────

    @Test
    fun `TaskDto maps to TaskEntity with SYNCED status`() {
        val dto = TaskDto(
            id = "TASK-0001",
            type = "DROP",
            status = "DELIVERED",
            riderId = "RIDER-001",
            customerName = "Test User",
            customerPhone = "+910000000000",
            address = "Test Address",
            description = "Test Desc",
            createdAt = 5000L,
            updatedAt = 6000L
        )

        val entity = dto.toEntity()

        assertEquals("TASK-0001", entity.id)
        assertEquals("DROP", entity.type)
        assertEquals("DELIVERED", entity.status)
        assertEquals("SYNCED", entity.syncStatus)  // DTOs from server are always SYNCED
    }

    // ── TaskActionEntity -> TaskAction (Domain) ──────────────────

    @Test
    fun `TaskActionEntity maps to TaskAction domain model`() {
        val entity = TaskActionEntity(
            id = "action-1",
            taskId = "TASK-0001",
            actionType = "REACH",
            timestamp = 7000L,
            latitude = 12.97,
            longitude = 77.59,
            notes = "Arrived at location",
            isSynced = true,
            retryCount = 0,
            lastError = null
        )

        val domain = entity.toDomain()

        assertEquals("action-1", domain.id)
        assertEquals("TASK-0001", domain.taskId)
        assertEquals(ActionType.REACH, domain.actionType)
        assertEquals(7000L, domain.timestamp)
        assertEquals(12.97, domain.latitude!!, 0.001)
        assertEquals(77.59, domain.longitude!!, 0.001)
        assertEquals("Arrived at location", domain.notes)
        assertTrue(domain.isSynced)
    }

    @Test
    fun `TaskActionEntity with null optionals maps correctly`() {
        val entity = TaskActionEntity(
            id = "action-2",
            taskId = "TASK-0002",
            actionType = "DELIVER",
            timestamp = 8000L,
            isSynced = false
        )

        val domain = entity.toDomain()

        assertNull(domain.latitude)
        assertNull(domain.longitude)
        assertNull(domain.notes)
        assertFalse(domain.isSynced)
    }

    // ── TaskActionEntity -> TaskActionDto ─────────────────────────

    @Test
    fun `TaskActionEntity maps to DTO for sync`() {
        val entity = TaskActionEntity(
            id = "action-3",
            taskId = "TASK-0003",
            actionType = "FAIL_DELIVERY",
            timestamp = 9000L,
            notes = "Customer unavailable",
            isSynced = false,
            retryCount = 2,
            lastError = "Previous error"
        )

        val dto = entity.toDto()

        assertEquals("action-3", dto.id)
        assertEquals("TASK-0003", dto.taskId)
        assertEquals("FAIL_DELIVERY", dto.actionType)
        assertEquals(9000L, dto.timestamp)
        assertEquals("Customer unavailable", dto.notes)
        // retryCount and lastError are NOT sent to API
    }

    // ── Round-trip mapping ────────────────────────────────────────

    @Test
    fun `Task round-trips through Entity without data loss`() {
        val original = Task(
            id = "TASK-ROUND",
            type = TaskType.DROP,
            status = TaskStatus.FAILED_DELIVERY,
            riderId = "RIDER-001",
            customerName = "Round Trip",
            customerPhone = "+910000000000",
            address = "Round Trip Address",
            description = "Round Trip Desc",
            createdAt = 10000L,
            updatedAt = 11000L,
            syncStatus = SyncStatus.FAILED
        )

        val roundTripped = original.toEntity().toDomain()

        assertEquals(original, roundTripped)
    }
}

package com.example.riderapp.data.mapper

import com.example.riderapp.data.local.entity.TaskActionEntity
import com.example.riderapp.data.local.entity.TaskEntity
import com.example.riderapp.data.remote.dto.TaskActionDto
import com.example.riderapp.data.remote.dto.TaskDto
import com.example.riderapp.domain.model.*
import com.example.riderapp.util.Constants

object TaskMapper {

    fun TaskEntity.toDomain(): Task {
        return Task(
            id = id,
            type = TaskType.valueOf(type),
            status = TaskStatus.valueOf(status),
            riderId = riderId,
            customerName = customerName,
            customerPhone = customerPhone,
            address = address,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt,
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }

    fun Task.toEntity(): TaskEntity {
        return TaskEntity(
            id = id,
            type = type.name,
            status = status.name,
            riderId = riderId,
            customerName = customerName,
            customerPhone = customerPhone,
            address = address,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt,
            syncStatus = syncStatus.name
        )
    }

    /**
     * Maps API DTO to Room entity.
     * Overrides riderId to the local rider — in production the server
     * only returns tasks for the authenticated rider, but the mock API
     * returns tasks for multiple riders.
     */
    fun TaskDto.toEntity(): TaskEntity {
        return TaskEntity(
            id = id,
            type = type,
            status = status,
            riderId = Constants.RIDER_ID,
            customerName = customerName,
            customerPhone = customerPhone,
            address = address,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt,
            syncStatus = "SYNCED"
        )
    }

    fun TaskActionEntity.toDomain(): TaskAction {
        return TaskAction(
            id = id,
            taskId = taskId,
            actionType = ActionType.valueOf(actionType),
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            notes = notes,
            isSynced = isSynced
        )
    }

    fun TaskAction.toEntity(): TaskActionEntity {
        return TaskActionEntity(
            id = id,
            taskId = taskId,
            actionType = actionType.name,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            notes = notes,
            isSynced = isSynced
        )
    }

    fun TaskActionEntity.toDto(): TaskActionDto {
        return TaskActionDto(
            id = id,
            taskId = taskId,
            actionType = actionType,
            timestamp = timestamp,
            notes = notes
        )
    }
}

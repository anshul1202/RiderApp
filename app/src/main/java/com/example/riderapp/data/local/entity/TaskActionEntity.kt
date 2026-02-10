package com.example.riderapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_actions",
    indices = [Index(value = ["taskId"])]
)
data class TaskActionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val actionType: String,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    val isSynced: Boolean = false,
    val retryCount: Int = 0,
    val lastError: String? = null
)

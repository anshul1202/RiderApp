package com.example.riderapp.domain.model

data class Task(
    val id: String,
    val type: TaskType,
    val status: TaskStatus,
    val riderId: String,
    val customerName: String,
    val customerPhone: String,
    val address: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

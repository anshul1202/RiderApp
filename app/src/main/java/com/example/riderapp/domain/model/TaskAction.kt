package com.example.riderapp.domain.model

data class TaskAction(
    val id: String,
    val taskId: String,
    val actionType: ActionType,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    val isSynced: Boolean = false
)

package com.example.riderapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val type: String,
    val status: String,
    val riderId: String,
    val customerName: String,
    val customerPhone: String,
    val address: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "SYNCED"
)

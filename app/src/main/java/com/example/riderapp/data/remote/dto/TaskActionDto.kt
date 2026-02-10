package com.example.riderapp.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TaskActionDto(
    @SerializedName("id") val id: String,
    @SerializedName("taskId") val taskId: String,
    @SerializedName("actionType") val actionType: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("notes") val notes: String? = null
)

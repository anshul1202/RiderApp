package com.example.riderapp.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SyncRequest(
    @SerializedName("actions") val actions: List<TaskActionDto>
)

data class SyncResponse(
    @SerializedName("syncedIds") val syncedIds: List<String>? = null,
    @SerializedName("failedIds") val failedIds: List<String>? = null,
    @SerializedName("errors") val errors: List<String>? = null
)

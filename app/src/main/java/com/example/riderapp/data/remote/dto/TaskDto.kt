package com.example.riderapp.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TaskDto(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("status") val status: String,
    @SerializedName("riderId") val riderId: String,
    @SerializedName("customerName") val customerName: String,
    @SerializedName("customerPhone") val customerPhone: String,
    @SerializedName("address") val address: String,
    @SerializedName("description") val description: String,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long
)

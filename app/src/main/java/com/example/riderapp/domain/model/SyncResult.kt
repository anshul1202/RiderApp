package com.example.riderapp.domain.model

sealed class SyncResult {
    data class Success(val syncedCount: Int) : SyncResult()
    data class PartialSuccess(
        val syncedCount: Int,
        val failedCount: Int,
        val errors: List<String>
    ) : SyncResult()
    data class Failure(val error: String) : SyncResult()
}

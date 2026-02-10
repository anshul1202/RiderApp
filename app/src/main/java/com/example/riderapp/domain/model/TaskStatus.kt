package com.example.riderapp.domain.model

enum class TaskStatus(val displayName: String) {
    ASSIGNED("Assigned"),
    REACHED("Reached"),
    PICKED_UP("Picked Up"),
    DELIVERED("Delivered"),
    FAILED_PICKUP("Failed Pickup"),
    FAILED_DELIVERY("Failed Delivery"),
    RETURNED("Returned")
}

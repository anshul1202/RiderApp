package com.example.riderapp.domain.model

enum class ActionType(val displayName: String) {
    REACH("Reach Location"),
    PICK_UP("Pick Up"),
    DELIVER("Deliver"),
    FAIL_PICKUP("Fail Pickup"),
    FAIL_DELIVERY("Fail Delivery"),
    RETURN("Return");

    companion object {
        fun getAvailableActions(taskType: TaskType, currentStatus: TaskStatus): List<ActionType> {
            return when (taskType) {
                TaskType.PICKUP -> when (currentStatus) {
                    TaskStatus.ASSIGNED -> listOf(REACH)
                    TaskStatus.REACHED -> listOf(PICK_UP, FAIL_PICKUP)
                    else -> emptyList()
                }
                TaskType.DROP -> when (currentStatus) {
                    TaskStatus.ASSIGNED -> listOf(REACH)
                    TaskStatus.REACHED -> listOf(DELIVER, FAIL_DELIVERY)
                    TaskStatus.FAILED_DELIVERY -> listOf(RETURN)
                    else -> emptyList()
                }
            }
        }

        fun getResultingStatus(action: ActionType): TaskStatus {
            return when (action) {
                REACH -> TaskStatus.REACHED
                PICK_UP -> TaskStatus.PICKED_UP
                DELIVER -> TaskStatus.DELIVERED
                FAIL_PICKUP -> TaskStatus.FAILED_PICKUP
                FAIL_DELIVERY -> TaskStatus.FAILED_DELIVERY
                RETURN -> TaskStatus.RETURNED
            }
        }
    }
}

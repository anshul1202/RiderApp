package com.example.riderapp.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the task state machine — verifies that correct actions
 * are available for each task type + status combination, and that
 * actions produce the correct resulting status.
 */
class ActionTypeTest {

    // ── Available actions for PICKUP tasks ────────────────────────

    @Test
    fun `PICKUP ASSIGNED - only REACH is available`() {
        val actions = ActionType.getAvailableActions(TaskType.PICKUP, TaskStatus.ASSIGNED)
        assertEquals(listOf(ActionType.REACH), actions)
    }

    @Test
    fun `PICKUP REACHED - PICK_UP and FAIL_PICKUP are available`() {
        val actions = ActionType.getAvailableActions(TaskType.PICKUP, TaskStatus.REACHED)
        assertEquals(listOf(ActionType.PICK_UP, ActionType.FAIL_PICKUP), actions)
    }

    @Test
    fun `PICKUP PICKED_UP - no actions available (terminal state)`() {
        val actions = ActionType.getAvailableActions(TaskType.PICKUP, TaskStatus.PICKED_UP)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `PICKUP FAILED_PICKUP - no actions available (terminal state)`() {
        val actions = ActionType.getAvailableActions(TaskType.PICKUP, TaskStatus.FAILED_PICKUP)
        assertTrue(actions.isEmpty())
    }

    // ── Available actions for DROP tasks ──────────────────────────

    @Test
    fun `DROP ASSIGNED - only REACH is available`() {
        val actions = ActionType.getAvailableActions(TaskType.DROP, TaskStatus.ASSIGNED)
        assertEquals(listOf(ActionType.REACH), actions)
    }

    @Test
    fun `DROP REACHED - DELIVER and FAIL_DELIVERY are available`() {
        val actions = ActionType.getAvailableActions(TaskType.DROP, TaskStatus.REACHED)
        assertEquals(listOf(ActionType.DELIVER, ActionType.FAIL_DELIVERY), actions)
    }

    @Test
    fun `DROP FAILED_DELIVERY - only RETURN is available`() {
        val actions = ActionType.getAvailableActions(TaskType.DROP, TaskStatus.FAILED_DELIVERY)
        assertEquals(listOf(ActionType.RETURN), actions)
    }

    @Test
    fun `DROP DELIVERED - no actions available (terminal state)`() {
        val actions = ActionType.getAvailableActions(TaskType.DROP, TaskStatus.DELIVERED)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `DROP RETURNED - no actions available (terminal state)`() {
        val actions = ActionType.getAvailableActions(TaskType.DROP, TaskStatus.RETURNED)
        assertTrue(actions.isEmpty())
    }

    // ── Resulting status from actions ─────────────────────────────

    @Test
    fun `REACH action results in REACHED status`() {
        assertEquals(TaskStatus.REACHED, ActionType.getResultingStatus(ActionType.REACH))
    }

    @Test
    fun `PICK_UP action results in PICKED_UP status`() {
        assertEquals(TaskStatus.PICKED_UP, ActionType.getResultingStatus(ActionType.PICK_UP))
    }

    @Test
    fun `DELIVER action results in DELIVERED status`() {
        assertEquals(TaskStatus.DELIVERED, ActionType.getResultingStatus(ActionType.DELIVER))
    }

    @Test
    fun `FAIL_PICKUP action results in FAILED_PICKUP status`() {
        assertEquals(TaskStatus.FAILED_PICKUP, ActionType.getResultingStatus(ActionType.FAIL_PICKUP))
    }

    @Test
    fun `FAIL_DELIVERY action results in FAILED_DELIVERY status`() {
        assertEquals(TaskStatus.FAILED_DELIVERY, ActionType.getResultingStatus(ActionType.FAIL_DELIVERY))
    }

    @Test
    fun `RETURN action results in RETURNED status`() {
        assertEquals(TaskStatus.RETURNED, ActionType.getResultingStatus(ActionType.RETURN))
    }

    // ── Full PICKUP workflow ─────────────────────────────────────

    @Test
    fun `full PICKUP happy path - ASSIGNED to PICKED_UP`() {
        var status = TaskStatus.ASSIGNED

        // Step 1: Reach
        val reachActions = ActionType.getAvailableActions(TaskType.PICKUP, status)
        assertTrue(reachActions.contains(ActionType.REACH))
        status = ActionType.getResultingStatus(ActionType.REACH)
        assertEquals(TaskStatus.REACHED, status)

        // Step 2: Pick up
        val pickupActions = ActionType.getAvailableActions(TaskType.PICKUP, status)
        assertTrue(pickupActions.contains(ActionType.PICK_UP))
        status = ActionType.getResultingStatus(ActionType.PICK_UP)
        assertEquals(TaskStatus.PICKED_UP, status)

        // Terminal
        assertTrue(ActionType.getAvailableActions(TaskType.PICKUP, status).isEmpty())
    }

    // ── Full DROP workflow with failure ───────────────────────────

    @Test
    fun `full DROP failure path - ASSIGNED to RETURNED`() {
        var status = TaskStatus.ASSIGNED

        // Step 1: Reach
        status = ActionType.getResultingStatus(ActionType.REACH)
        assertEquals(TaskStatus.REACHED, status)

        // Step 2: Fail delivery
        status = ActionType.getResultingStatus(ActionType.FAIL_DELIVERY)
        assertEquals(TaskStatus.FAILED_DELIVERY, status)

        // Step 3: Return
        val returnActions = ActionType.getAvailableActions(TaskType.DROP, status)
        assertTrue(returnActions.contains(ActionType.RETURN))
        status = ActionType.getResultingStatus(ActionType.RETURN)
        assertEquals(TaskStatus.RETURNED, status)

        // Terminal
        assertTrue(ActionType.getAvailableActions(TaskType.DROP, status).isEmpty())
    }

    // ── Display names ────────────────────────────────────────────

    @Test
    fun `all action types have non-blank display names`() {
        ActionType.values().forEach { action ->
            assertTrue(
                "ActionType.${action.name} has blank displayName",
                action.displayName.isNotBlank()
            )
        }
    }

    @Test
    fun `all task statuses have non-blank display names`() {
        TaskStatus.values().forEach { status ->
            assertTrue(
                "TaskStatus.${status.name} has blank displayName",
                status.displayName.isNotBlank()
            )
        }
    }
}

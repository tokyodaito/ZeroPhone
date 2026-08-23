package com.numenlabs.zerophone.feature.home

import com.numenlabs.zerophone.core.context.AvailabilityState
import com.numenlabs.zerophone.core.context.LogicalCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quick actions stay in lock-step with the logical capability catalog: every
 * action binds to exactly one capability and together they cover the catalog,
 * so a quick action can never dangle off an unknown capability id.
 */
class QuickActionTest {

    @Test
    fun `every quick action binds to a distinct known capability`() {
        val engineGoverned = QuickAction.entries
            .filter { it != QuickAction.SEND_TO_PC }
            .map { it.capabilityId }

        assertEquals(LogicalCapabilities.ALL, engineGoverned)
        assertEquals(engineGoverned.distinct().size, engineGoverned.size)

        // Send-to-PC is a sync action, not an engine capability.
        assertEquals(SEND_TO_PC_CAPABILITY, QuickAction.SEND_TO_PC.capabilityId)
        assertFalse(LogicalCapabilities.ALL.contains(SEND_TO_PC_CAPABILITY))
    }

    @Test
    fun `quick action stays actionable in every engine state except a full block`() {
        val actionable = listOf(
            AvailabilityState.Available,
            AvailabilityState.Restricted(com.numenlabs.zerophone.core.context.RestrictionReason.FOCUS_MODE),
            AvailabilityState.TemporarilyAvailable(remainingMillis = 60_000L),
            AvailabilityState.Contextual()
        )

        actionable.forEach { state ->
            assertTrue("expected actionable: $state", state.allowsQuickAction)
        }
        assertFalse(AvailabilityState.Blocked.allowsQuickAction)
    }
}

package com.numenlabs.zerophone.desktop.state

import com.numenlabs.zerophone.core.model.Availability
import com.numenlabs.zerophone.core.model.CapabilityAvailability
import com.numenlabs.zerophone.core.model.CapabilityKind
import com.numenlabs.zerophone.core.model.EmergencyWindow
import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.PolicySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateMappingTest {

    @Test
    fun `dashboard model aggregates mode, emergency and availability rows`() {
        val state = DesktopAppState(
            snapshot = PolicySnapshot(
                generatedAtMillis = 99,
                deviceId = "phone-1",
                activeMode = "work",
                emergencyRemainingMillis = 45000,
                capabilities = listOf(
                    CapabilityAvailability(id = "com.yt", state = Availability.BLOCKED),
                    CapabilityAvailability(
                        id = "call",
                        kind = CapabilityKind.LOGICAL,
                        label = "Позвонить",
                        state = Availability.RESTRICTED,
                        restrictionReason = "FOCUS_MODE"
                    ),
                    CapabilityAvailability(
                        id = "games",
                        state = Availability.TEMPORARILY_AVAILABLE,
                        remainingMillis = 120000
                    ),
                    CapabilityAvailability(
                        id = "maps",
                        state = Availability.CONTEXTUAL,
                        contextCondition = "not at night"
                    )
                )
            )
        )

        val dashboard = state.toDashboardModel()

        assertEquals("work", dashboard.activeMode)
        assertTrue(dashboard.emergency is EmergencyWindow.Active)
        assertEquals(45000L, (dashboard.emergency as EmergencyWindow.Active).remainingMillis)
        assertEquals(4, dashboard.rows.size)
        assertEquals("com.yt", dashboard.rows[0].id)
        assertEquals(1, dashboard.countByState[Availability.BLOCKED])
        assertEquals("FOCUS_MODE", dashboard.rows[1].detail)
        assertEquals("Позвонить", dashboard.rows[1].label)
        assertEquals("remaining 120s", dashboard.rows[2].detail)
        assertEquals("not at night", dashboard.rows[3].detail)
    }

    @Test
    fun `dashboard without emergency maps to None`() {
        val state = DesktopAppState(
            snapshot = PolicySnapshot(deviceId = "d", emergencyRemainingMillis = 0)
        )
        assertTrue(state.toDashboardModel().emergency is EmergencyWindow.None)
    }

    @Test
    fun `empty state maps to empty dashboard`() {
        val dashboard = DesktopAppState().toDashboardModel()
        assertNull(dashboard.activeMode)
        assertTrue(dashboard.emergency is EmergencyWindow.None)
        assertTrue(dashboard.rows.isEmpty())
    }

    @Test
    fun `inbox entries expose link fields and opened mark`() {
        val state = DesktopAppState(
            inbox = listOf(
                InboxItem(
                    link = LinkPayload(
                        id = "l1",
                        url = "https://example.com/a",
                        title = "Статья",
                        sourceDeviceName = "Pixel",
                        sentAtMillis = 10
                    ),
                    receivedAtMillis = 20,
                    isOpened = true
                )
            )
        )
        val entry = state.toInboxEntries().single()
        assertEquals("l1", entry.id)
        assertEquals("https://example.com/a", entry.url)
        assertEquals("Статья", entry.title)
        assertEquals("Pixel", entry.sourceDeviceName)
        assertEquals(10L, entry.sentAtMillis)
        assertEquals(20L, entry.receivedAtMillis)
        assertTrue(entry.isOpened)
    }

    @Test
    fun `unopenedCount counts only unopened items`() {
        val state = DesktopAppState(
            inbox = listOf(
                InboxItem(LinkPayload(id = "a", url = "u"), receivedAtMillis = 1, isOpened = true),
                InboxItem(LinkPayload(id = "b", url = "u"), receivedAtMillis = 2)
            )
        )
        assertEquals(1, state.unopenedCount)
    }
}

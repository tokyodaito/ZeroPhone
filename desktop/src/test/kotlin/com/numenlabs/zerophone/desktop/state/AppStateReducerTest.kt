package com.numenlabs.zerophone.desktop.state

import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.PolicySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateReducerTest {

    private val link = LinkPayload(id = "l1", url = "https://example.com", sentAtMillis = 10)

    @Test
    fun `connection events map to connection state`() {
        var state = DesktopAppState()
        state = reduce(state, SyncEvent.PairingRequired)
        assertEquals(ConnectionState.NEEDS_PAIRING, state.connection)
        state = reduce(state, SyncEvent.Connecting)
        assertEquals(ConnectionState.CONNECTING, state.connection)
        state = reduce(state, SyncEvent.Connected)
        assertEquals(ConnectionState.CONNECTED, state.connection)
        state = reduce(state, SyncEvent.Disconnected("boom"))
        assertEquals(ConnectionState.DISCONNECTED, state.connection)
    }

    @Test
    fun `snapshot replaces previous and stamps lastSyncAt`() {
        val first = PolicySnapshot(deviceId = "d", generatedAtMillis = 1)
        val second = PolicySnapshot(deviceId = "d", generatedAtMillis = 2, activeMode = "work")

        var state = reduce(DesktopAppState(), SyncEvent.SnapshotReceived(first), nowMillis = 100)
        assertEquals(first, state.snapshot)
        assertEquals(100L, state.lastSyncAtMillis)

        state = reduce(state, SyncEvent.SnapshotReceived(second), nowMillis = 200)
        assertEquals(second, state.snapshot)
        assertEquals("work", state.snapshot?.activeMode)
        assertEquals(200L, state.lastSyncAtMillis)
    }

    @Test
    fun `received link is prepended with receivedAt timestamp`() {
        val state = reduce(DesktopAppState(), SyncEvent.LinkReceived(link), nowMillis = 555)
        val item = state.inbox.single()
        assertEquals("l1", item.id)
        assertEquals(link, item.link)
        assertEquals(555L, item.receivedAtMillis)
        assertEquals(1, state.unopenedCount)
    }

    @Test
    fun `same link id is not duplicated`() {
        var state = reduce(DesktopAppState(), SyncEvent.LinkReceived(link), nowMillis = 1)
        state = reduce(state, SyncEvent.LinkReceived(link), nowMillis = 2)
        assertEquals(1, state.inbox.size)
        assertEquals(1L, state.inbox.single().receivedAtMillis)
    }

    @Test
    fun `different link ids accumulate newest first`() {
        val link2 = link.copy(id = "l2")
        var state = DesktopAppState()
        state = reduce(state, SyncEvent.LinkReceived(link), nowMillis = 1)
        state = reduce(state, SyncEvent.LinkReceived(link2), nowMillis = 2)
        assertEquals(listOf("l2", "l1"), state.inbox.map { it.id })
    }

    @Test
    fun `inbox is capped at max size dropping oldest`() {
        var state = DesktopAppState()
        repeat(DesktopAppState.MAX_INBOX_ITEMS + 10) { index ->
            state = reduce(
                state,
                SyncEvent.LinkReceived(link.copy(id = "l$index")),
                nowMillis = index.toLong()
            )
        }
        assertEquals(DesktopAppState.MAX_INBOX_ITEMS, state.inbox.size)
        assertEquals("l209", state.inbox.first().id)
        assertEquals("l10", state.inbox.last().id)
    }

    @Test
    fun `opening marks only the matching item`() {
        var state = DesktopAppState()
        state = reduce(state, SyncEvent.LinkReceived(link), nowMillis = 1)
        state = reduce(state, SyncEvent.LinkReceived(link.copy(id = "l2")), nowMillis = 2)
        state = reduce(state, SyncEvent.InboxItemOpened("l2"))
        // Inbox is newest-first: [l2, l1], so l2 (index 0) gets the mark.
        assertEquals(listOf(true, false), state.inbox.map { it.isOpened })
        assertEquals(1, state.unopenedCount)
    }

    @Test
    fun `opening unknown id is a no-op`() {
        var state = reduce(DesktopAppState(), SyncEvent.LinkReceived(link), nowMillis = 1)
        state = reduce(state, SyncEvent.InboxItemOpened("missing"))
        assertEquals(listOf(false), state.inbox.map { it.isOpened })
    }

    @Test
    fun `reducer is pure - input state is not mutated`() {
        val original = DesktopAppState()
        val after = reduce(original, SyncEvent.LinkReceived(link), nowMillis = 1)
        assertTrue(original.inbox.isEmpty())
        assertEquals(1, after.inbox.size)
    }

    @Test
    fun `replaying the same event sequence is deterministic`() {
        fun replay() =
            listOf(
                SyncEvent.Connecting,
                SyncEvent.Connected,
                SyncEvent.SnapshotReceived(PolicySnapshot(deviceId = "d")),
                SyncEvent.LinkReceived(link),
                SyncEvent.InboxItemOpened("l1"),
                SyncEvent.Disconnected("closed")
            ).fold(DesktopAppState()) { state, event -> reduce(state, event, nowMillis = 42) }

        assertEquals(replay(), replay())
    }
}

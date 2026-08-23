package com.numenlabs.zerophone.desktop.state

import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.PolicySnapshot
import com.numenlabs.zerophone.desktop.sync.InboxStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppStateHolderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val link = LinkPayload(id = "l1", url = "https://example.com", sentAtMillis = 1)

    private fun newHolder(store: InboxStore = InboxStore(temp.newFolder().toPath())): AppStateHolder =
        AppStateHolder(inboxStore = store, timeSource = { 1_000L })

    @Test
    fun `holder exposes state flow updated by dispatched events`() = runTest {
        val holder = newHolder()
        assertEquals(DesktopAppState(inbox = emptyList()), holder.state.first())

        holder.dispatch(SyncEvent.Connecting)
        holder.dispatch(SyncEvent.Connected)
        holder.dispatch(SyncEvent.SnapshotReceived(PolicySnapshot(deviceId = "d")))

        val state = holder.state.first()
        assertEquals(ConnectionState.CONNECTED, state.connection)
        assertEquals("d", state.snapshot?.deviceId)
        assertEquals(1_000L, state.lastSyncAtMillis)
    }

    @Test
    fun `link received lands in inbox and is persisted`() = runTest {
        val dir = temp.newFolder().toPath()
        val holder = newHolder(InboxStore(dir))

        holder.dispatch(SyncEvent.LinkReceived(link))

        assertEquals("l1", holder.state.first().inbox.single().id)
        // A fresh holder over the same directory (app restart) loads the inbox.
        val restarted = AppStateHolder(inboxStore = InboxStore(dir), timeSource = { 2_000L })
        val restored = restarted.state.first().inbox.single()
        assertEquals("l1", restored.id)
        assertFalse(restored.isOpened)
    }

    @Test
    fun `markOpened persists the opened mark across restart`() = runTest {
        val dir = temp.newFolder().toPath()
        val holder = newHolder(InboxStore(dir))
        holder.dispatch(SyncEvent.LinkReceived(link))
        holder.markOpened("l1")
        assertTrue(holder.state.first().inbox.single().isOpened)

        val restarted = AppStateHolder(inboxStore = InboxStore(dir), timeSource = { 2_000L })
        assertTrue(restarted.state.first().inbox.single().isOpened)
        assertEquals(0, restarted.state.first().unopenedCount)
    }

    @Test
    fun `full sync sequence converges to expected state`() = runTest {
        val holder = newHolder()
        listOf(
            SyncEvent.Connecting,
            SyncEvent.Connected,
            SyncEvent.SnapshotReceived(PolicySnapshot(deviceId = "d", activeMode = "work")),
            SyncEvent.LinkReceived(link),
            SyncEvent.LinkReceived(link.copy(id = "l2")),
            SyncEvent.Disconnected("socket closed"),
            SyncEvent.Connecting,
            SyncEvent.Connected,
        ).forEach(holder::dispatch)

        val state = holder.state.first()
        assertEquals(ConnectionState.CONNECTED, state.connection)
        assertEquals(listOf("l2", "l1"), state.inbox.map { it.id })
        assertEquals("work", state.snapshot?.activeMode)
    }

    @Test
    fun `holder without store works purely in memory`() = runTest {
        val holder = AppStateHolder(timeSource = { 5L })
        holder.dispatch(SyncEvent.LinkReceived(link))
        assertEquals(1, holder.state.first().inbox.size)
    }
}

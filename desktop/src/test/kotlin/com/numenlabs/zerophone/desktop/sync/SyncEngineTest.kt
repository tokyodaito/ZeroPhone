package com.numenlabs.zerophone.desktop.sync

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.PairingClaim
import com.numenlabs.zerophone.core.model.PolicySnapshot
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.core.model.SyncState
import com.numenlabs.zerophone.desktop.state.ConnectionState
import com.numenlabs.zerophone.desktop.state.DesktopAppState
import com.numenlabs.zerophone.desktop.state.SyncEvent
import com.numenlabs.zerophone.desktop.state.reduce
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * [SyncEngine] semantics against a scripted fake transport and a tmp-dir
 * token store: the pairing gate, conditional pulls, push-triggered
 * re-syncs, conditional pushes and the unauthorized path — all in virtual
 * time with an injected sleeper.
 */
class SyncEngineTest {

    private lateinit var dir: Path
    private lateinit var tokenStore: DeviceTokenStore
    private val delays = mutableListOf<Long>()

    private inner class FakeTransport : SyncTransport {
        val pullCalls = mutableListOf<Long?>()
        val pushCalls = mutableListOf<StateUpdateRequest>()
        val claims = mutableListOf<PairingClaim>()
        var pullResult: suspend (Long?) -> PullResult = { PullResult.NotModified }
        var pushResult: PushResult = PushResult.BadRequest
        var claimResult: PairingResult = PairingResult.Rejected
        var socket: Flow<SyncPushMessage> = MutableSharedFlow()

        override suspend fun pull(baseRevision: Long?): PullResult {
            pullCalls.add(baseRevision)
            return pullResult(baseRevision)
        }

        override suspend fun push(update: StateUpdateRequest): PushResult {
            pushCalls.add(update)
            return pushResult
        }

        override suspend fun claim(claim: PairingClaim): PairingResult {
            claims.add(claim)
            return claimResult
        }

        override fun pushes(): Flow<SyncPushMessage> = socket
    }

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("sync-engine-test")
        tokenStore = DeviceTokenStore(dir)
        delays.clear()
    }

    @After
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun snapshot(mode: String) =
        SyncState(policy = PolicySnapshot(deviceId = "phone", activeMode = mode))

    private fun envelope(revision: Long, mode: String) =
        StateEnvelope(state = snapshot(mode), revision = revision)

    /** Runs the engine loop as a cancellable child; the block asserts in between. */
    private suspend fun TestScope.runEngine(engine: SyncEngine, block: suspend () -> Unit) {
        val loop = launch { engine.runLoop() }
        try {
            block()
        } finally {
            loop.cancel()
        }
    }

    @Test
    fun `without a token the engine parks on pairing and never pulls`() = runTest {
        val transport = FakeTransport()
        val events = mutableListOf<SyncEvent>()
        val engine = newEngine(transport, events)

        runEngine(engine) {
            advanceUntilIdle()
            assertEquals(listOf<SyncEvent>(SyncEvent.PairingRequired), events)
            assertTrue(transport.pullCalls.isEmpty())
        }
    }

    @Test
    fun `pairing claims stores credentials and resumes the loop`() = runTest {
        val transport = FakeTransport()
        transport.claimResult = PairingResult.Claimed(
            DeviceCredentials(deviceId = "desktop-1", token = "t", deviceName = "macbook")
        )
        transport.pullResult = { PullResult.Envelope(envelope(revision = 5, mode = "work")) }
        val events = mutableListOf<SyncEvent>()
        val engine = newEngine(transport, events)

        runEngine(engine) {
            advanceUntilIdle()
            assertEquals(PairingOutcome.PAIRED, engine.pair("422222", "macbook"))
            advanceUntilIdle()

            assertEquals("422222", transport.claims.single().code)
            assertEquals("macbook", transport.claims.single().deviceName)
            assertEquals("desktop-1", tokenStore.load()?.deviceId)

            assertEquals(SyncEvent.Connected, events.last())
            assertTrue(events.contains(SyncEvent.SnapshotReceived(envelope(5, "work").state.policy)))
            // First sync is a full pull: no baseRevision yet.
            assertEquals(null, transport.pullCalls.first())
        }
    }

    @Test
    fun `a 304 keeps the connection without a snapshot event`() = runTest {
        tokenStore.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        transport.pullResult = { PullResult.NotModified }
        val events = mutableListOf<SyncEvent>()
        val engine = newEngine(transport, events)

        runEngine(engine) {
            advanceUntilIdle()
            assertEquals(SyncEvent.Connected, events.last())
            assertTrue(events.none { it is SyncEvent.SnapshotReceived })
        }
    }

    @Test
    fun `state updated ahead of the tracked revision triggers one conditional repull`() = runTest {
        tokenStore.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        var revision = 5L
        transport.pullResult = { PullResult.Envelope(envelope(revision, "work")) }
        val socket = MutableSharedFlow<SyncPushMessage>(extraBufferCapacity = 16)
        transport.socket = socket
        val events = mutableListOf<SyncEvent>()
        val engine = newEngine(transport, events)

        runEngine(engine) {
            advanceUntilIdle()
            transport.pullCalls.clear()

            revision = 7
            socket.tryEmit(SyncPushMessage.StateUpdated(revision = 7, actorDeviceId = "phone", hint = "policy"))
            advanceUntilIdle()

            assertEquals(listOf<Long?>(5), transport.pullCalls)
            val snapshots = events.filterIsInstance<SyncEvent.SnapshotReceived>()
            assertEquals("work", snapshots.last().snapshot.activeMode)
            assertTrue(snapshots.size >= 2)
        }
    }

    @Test
    fun `state updated at or below the tracked revision pulls nothing`() = runTest {
        tokenStore.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        transport.pullResult = { PullResult.Envelope(envelope(6, "work")) }
        val socket = MutableSharedFlow<SyncPushMessage>(extraBufferCapacity = 16)
        transport.socket = socket
        val engine = newEngine(transport, mutableListOf())

        runEngine(engine) {
            advanceUntilIdle()
            transport.pullCalls.clear()

            socket.tryEmit(SyncPushMessage.StateUpdated(revision = 6, actorDeviceId = "phone"))
            socket.tryEmit(SyncPushMessage.StateUpdated(revision = 3, actorDeviceId = "phone"))
            socket.tryEmit(SyncPushMessage.StateSnapshot(revision = 6))
            advanceUntilIdle()

            assertTrue(transport.pullCalls.isEmpty())
        }
    }

    @Test
    fun `link pushes are forwarded as events`() = runTest {
        tokenStore.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        transport.pullResult = { PullResult.NotModified }
        val socket = MutableSharedFlow<SyncPushMessage>(extraBufferCapacity = 16)
        transport.socket = socket
        val events = mutableListOf<SyncEvent>()
        val engine = newEngine(transport, events)

        runEngine(engine) {
            advanceUntilIdle()

            val link = LinkPayload(id = "l1", url = "https://example.com", sentAtMillis = 1)
            socket.tryEmit(SyncPushMessage.LinkReceived(link))
            advanceUntilIdle()

            assertEquals(SyncEvent.LinkReceived(link), events.last())
        }
    }

    @Test
    fun `push accepted applies the new revision`() = runTest {
        tokenStore.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        transport.pullResult = { PullResult.Envelope(envelope(2, "work")) }
        val events = mutableListOf<SyncEvent>()
        val engine = newEngine(transport, events)

        runEngine(engine) {
            advanceUntilIdle()

            transport.pushResult = PushResult.Accepted(envelope(3, "rest"))
            val outcome = engine.push(snapshot("rest"), hint = "mode change")
            advanceUntilIdle()

            assertTrue(outcome is PushResult.Accepted)
            assertEquals(2L, transport.pushCalls.single().baseRevision)
            assertEquals("mode change", transport.pushCalls.single().hint)
            assertEquals(
                "rest",
                events.filterIsInstance<SyncEvent.SnapshotReceived>().last().snapshot.activeMode,
            )
        }
    }

    @Test
    fun `push conflict applies the winning server state`() = runTest {
        tokenStore.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        transport.pullResult = { PullResult.Envelope(envelope(2, "work")) }
        val events = mutableListOf<SyncEvent>()
        val engine = newEngine(transport, events)

        runEngine(engine) {
            advanceUntilIdle()

            transport.pushResult = PushResult.Conflict(envelope(9, "rest"))
            val outcome = engine.push(snapshot("edit"), hint = "edit")
            advanceUntilIdle()

            assertTrue(outcome is PushResult.Conflict)
            assertEquals(
                "rest",
                events.filterIsInstance<SyncEvent.SnapshotReceived>().last().snapshot.activeMode,
            )
        }
    }

    @Test
    fun `unauthorized pulls re-enter the pairing gate`() = runTest {
        tokenStore.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        transport.pullResult = { PullResult.Unauthorized }
        val events = mutableListOf<SyncEvent>()
        val engine = newEngine(transport, events)

        runEngine(engine) {
            advanceUntilIdle()
            assertEquals(SyncEvent.PairingRequired, events.last())
            assertEquals(
                ConnectionState.NEEDS_PAIRING,
                reduce(DesktopAppState(), SyncEvent.PairingRequired).connection,
            )
        }
    }

    @Test
    fun `a dropped socket reports disconnected and backs off before reconnecting`() = runTest {
        tokenStore.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        transport.pullResult = { PullResult.NotModified }
        var dropsLeft = 1
        transport.socket = flow {
            if (dropsLeft > 0) {
                dropsLeft -= 1
                emit(SyncPushMessage.StateSnapshot(revision = 1))
                // Completes right away: the engine sees a dropped socket.
            } else {
                awaitCancellation()
            }
        }
        val events = mutableListOf<SyncEvent>()
        val engine = newEngine(transport, events)

        runEngine(engine) {
            advanceUntilIdle()

            val disconnects = events.filterIsInstance<SyncEvent.Disconnected>()
            assertTrue("expected disconnects, events=$events", disconnects.isNotEmpty())
            assertTrue(delays.isNotEmpty())
            // The loop survived and re-pulled.
            assertTrue(transport.pullCalls.size >= 2)
        }
    }

    private fun newEngine(
        transport: FakeTransport,
        events: MutableList<SyncEvent>,
    ): SyncEngine = SyncEngine(
        transport = transport,
        tokenStore = tokenStore,
        onEvent = { events.add(it) },
        sleeper = { delays.add(it) },
    )
}

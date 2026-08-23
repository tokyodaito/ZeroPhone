package com.numenlabs.zerophone.core.data.sync

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.PairingClaim
import com.numenlabs.zerophone.core.model.PolicySnapshot
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncState
import com.numenlabs.zerophone.core.policy.InMemoryPolicyRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PhoneSyncEngine] semantics on a scripted fake transport with in-memory
 * stores: the pairing gate, conditional pull/push, domain application via
 * [InMemoryPolicyRepository], send-to-PC and the pure backoff schedule —
 * all pure JVM, no Android APIs. The polling loop is driven step-wise
 * through virtual time (`runCurrent` / `advanceTimeBy`).
 */
class PhoneSyncEngineTest {

    private inner class FakeTransport : SyncTransport {
        val pullCalls = mutableListOf<Long?>()
        val pushCalls = mutableListOf<StateUpdateRequest>()
        val claims = mutableListOf<PairingClaim>()
        val links = mutableListOf<LinkPayload>()
        var pullResult: suspend (Long?) -> PullResult = { PullResult.NotModified }
        var pushResult: PushResult = PushResult.BadRequest
        var claimResult: PairingResult = PairingResult.Rejected
        var linkResult: LinkSendResult = LinkSendResult.Sent

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

        override suspend fun sendLink(payload: LinkPayload): LinkSendResult {
            links.add(payload)
            return linkResult
        }
    }

    private lateinit var credentials: InMemorySyncCredentialsStore
    private lateinit var policy: InMemoryPolicyRepository
    private val applied = mutableListOf<Unit>()
    private val delays = mutableListOf<Long>()

    @Before
    fun setUp() {
        credentials = InMemorySyncCredentialsStore()
        policy = InMemoryPolicyRepository()
        applied.clear()
        delays.clear()
    }

    private fun envelope(revision: Long, mode: String, emergencyMillis: Long = 0L) = StateEnvelope(
        state = SyncState(
            policy = PolicySnapshot(
                deviceId = "desktop",
                activeMode = mode,
                emergencyRemainingMillis = emergencyMillis,
            ),
        ),
        revision = revision,
    )

    private suspend fun TestScope.runEngine(engine: PhoneSyncEngine, block: suspend TestScope.() -> Unit) {
        val loop = launch { engine.runLoop() }
        try {
            block()
        } finally {
            loop.cancel()
        }
    }

    private fun newEngine(
        transport: FakeTransport,
        onEvent: (SyncEngineEvent) -> Unit = {},
    ): PhoneSyncEngine = PhoneSyncEngine(
        transport = transport,
        credentials = credentials,
        policy = policy,
        onEvent = onEvent,
        onPolicyApplied = { applied.add(Unit) },
        backoffMillis = { 1L },
        pollIntervalMillis = 1_000L,
        sleeper = {
            delays.add(it)
            delay(it)
        },
        timeSource = { 100_000L },
    )

    @Test
    fun `without a token the engine parks on pairing and never pulls`() = runTest {
        val transport = FakeTransport()
        val events = mutableListOf<SyncEngineEvent>()
        val engine = newEngine(transport) { events.add(it) }

        runEngine(engine) {
            runCurrent()
            assertEquals(listOf<SyncEngineEvent>(SyncEngineEvent.PairingRequired), events)
            assertTrue(transport.pullCalls.isEmpty())
        }
    }

    @Test
    fun `pairing claims stores credentials and applies the pulled state`() = runTest {
        val transport = FakeTransport()
        transport.claimResult = PairingResult.Claimed(
            DeviceCredentials(deviceId = "phone-1", token = "t", deviceName = "my phone")
        )
        transport.pullResult = { PullResult.Envelope(envelope(revision = 4, mode = "rest", emergencyMillis = 60_000)) }
        val engine = newEngine(transport)

        runEngine(engine) {
            runCurrent()
            assertEquals(PairingOutcome.PAIRED, engine.pair("422222"))
            runCurrent()

            assertEquals("phone", transport.claims.single().deviceKind)
            assertEquals("phone-1", credentials.load()?.deviceId)
            assertEquals(4L, credentials.revision())

            assertEquals("rest", policy.getActiveMode())
            assertEquals(160_000L, policy.getEmergencyDeadline())
            assertEquals(1, applied.size)

            assertEquals(null, transport.pullCalls.first())
        }
    }

    @Test
    fun `pairing rejection leaves the gate closed`() = runTest {
        val transport = FakeTransport()
        val engine = newEngine(transport)

        runEngine(engine) {
            runCurrent()
            assertEquals(PairingOutcome.REJECTED, engine.pair("000000"))
            assertEquals("000000", transport.claims.single().code)
            runCurrent()
            assertEquals(-1L, credentials.revision())
        }
    }

    @Test
    fun `a 304 keeps the connection without applying anything`() = runTest {
        credentials.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        transport.pullResult = { PullResult.NotModified }
        val events = mutableListOf<SyncEngineEvent>()
        val engine = newEngine(transport) { events.add(it) }

        runEngine(engine) {
            runCurrent()
            assertEquals(SyncEngineEvent.Connected, events.last())
            assertTrue(applied.isEmpty())
            assertEquals(-1L, credentials.revision())
        }
    }

    @Test
    fun `polling applies only fresher revisions`() = runTest {
        credentials.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        var revision = 2L
        transport.pullResult = { PullResult.Envelope(envelope(revision, "work")) }
        val engine = newEngine(transport)

        runEngine(engine) {
            runCurrent()
            assertEquals(2L, credentials.revision())
            assertEquals("work", policy.getActiveMode())

            revision = 5
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(5L, credentials.revision())
            assertTrue(transport.pullCalls.drop(1).all { it == 2L })
        }
    }

    @Test
    fun `polling skips stale revisions`() = runTest {
        credentials.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        transport.pullResult = { PullResult.Envelope(envelope(7, "work")) }
        val engine = newEngine(transport)

        runEngine(engine) {
            runCurrent()
            applied.clear()

            advanceTimeBy(1_000)
            runCurrent()

            assertTrue(applied.isEmpty())
            assertEquals(7L, credentials.revision())
        }
    }

    @Test
    fun `push serializes the domain and applies the accepted revision`() = runTest {
        credentials.save(DeviceCredentials(deviceId = "phone-9", token = "t"))
        policy.setActiveMode("focus")
        val transport = FakeTransport()
        transport.pullResult = { PullResult.NotModified }
        transport.pushResult = PushResult.Accepted(envelope(revision = 1, mode = "focus"))
        val engine = newEngine(transport)

        runEngine(engine) {
            runCurrent()

            val outcome = engine.pushCurrentState(hint = "mode change")

            assertTrue(outcome is PushResult.Accepted)
            assertEquals(0L, transport.pushCalls.single().baseRevision)
            assertEquals("mode change", transport.pushCalls.single().hint)
            assertEquals("focus", transport.pushCalls.single().state.policy.activeMode)
            assertEquals(1L, credentials.revision())
        }
    }

    @Test
    fun `push conflict applies the winning server state`() = runTest {
        credentials.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        transport.pullResult = { PullResult.NotModified }
        transport.pushResult = PushResult.Conflict(envelope(revision = 8, mode = "rest"))
        val engine = newEngine(transport)

        runEngine(engine) {
            runCurrent()

            val outcome = engine.pushCurrentState()

            assertTrue(outcome is PushResult.Conflict)
            assertEquals("rest", policy.getActiveMode())
            assertEquals(8L, credentials.revision())
        }
    }

    @Test
    fun `unauthorized pulls re-enter the pairing gate`() = runTest {
        credentials.save(DeviceCredentials(deviceId = "d", token = "revoked"))
        val transport = FakeTransport()
        transport.pullResult = { PullResult.Unauthorized }
        val events = mutableListOf<SyncEngineEvent>()
        val engine = newEngine(transport) { events.add(it) }

        runEngine(engine) {
            runCurrent()
            assertEquals(SyncEngineEvent.PairingRequired, events.last())
        }
    }

    @Test
    fun `sendLink carries the device identity and a fresh timestamp`() = runTest {
        credentials.save(DeviceCredentials(deviceId = "phone-9", token = "t", deviceName = "my phone"))
        val transport = FakeTransport()
        val engine = newEngine(transport)

        val result = engine.sendLink("https://example.com/a", title = "A")

        assertEquals(LinkSendResult.Sent, result)
        val payload = transport.links.single()
        assertEquals("https://example.com/a", payload.url)
        assertEquals("A", payload.title)
        assertEquals("phone-9", payload.sourceDeviceId)
        assertEquals("my phone", payload.sourceDeviceName)
        assertEquals(100_000L, payload.sentAtMillis)
        assertNotNull(payload.id)
    }

    @Test
    fun `sendLink surfaces rejection`() = runTest {
        credentials.save(DeviceCredentials(deviceId = "d", token = "t"))
        val transport = FakeTransport()
        transport.linkResult = LinkSendResult.Rejected
        val engine = newEngine(transport)

        assertEquals(LinkSendResult.Rejected, engine.sendLink("https://example.com"))
    }

    @Test
    fun `backoff schedule is exponential and capped`() {
        assertEquals(1_000L, PhoneSyncEngine.defaultBackoffMillis(0))
        assertEquals(2_000L, PhoneSyncEngine.defaultBackoffMillis(1))
        assertEquals(4_000L, PhoneSyncEngine.defaultBackoffMillis(2))
        assertEquals(16_000L, PhoneSyncEngine.defaultBackoffMillis(4))
        assertEquals(30_000L, PhoneSyncEngine.defaultBackoffMillis(5))
        assertEquals(30_000L, PhoneSyncEngine.defaultBackoffMillis(50))
    }
}

package com.numenlabs.zerophone.core.data.sync

import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.PairingClaim
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncState
import com.numenlabs.zerophone.core.policy.PolicyRepository
import com.numenlabs.zerophone.core.data.sync.PolicySyncMapper.applyTo
import com.numenlabs.zerophone.core.data.sync.PolicySyncMapper.toSyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow

/** Lifecycle events of the phone-side sync engine, for UI status. */
sealed interface SyncEngineEvent {
    data object PairingRequired : SyncEngineEvent
    data object Connecting : SyncEngineEvent
    data object Connected : SyncEngineEvent
    data class Disconnected(val reason: String? = null) : SyncEngineEvent
}

/** Result of a user-initiated pairing attempt. */
enum class PairingOutcome {
    PAIRED,
    REJECTED,
}

/**
 * Phone-side sync state machine over a [SyncTransport]:
 *
 *  - **Pairing gate** — with no stored token the engine reports
 *    [SyncEngineEvent.PairingRequired] and parks; [pair] claims the
 *    shortcode, stores the credentials and releases the gate. A rejected
 *    token (401) re-enters the same state.
 *  - **Pull** — conditional GET tracked by the stored revision (an
 *    up-to-date phone pays a 304). A fresh envelope is applied to the
 *    domain through [PolicyRepository] and [onPolicyApplied] then lets
 *    the composition re-run [com.numenlabs.zerophone.core.policy.PolicyApplier].
 *  - **Push** — conditional PUT of the domain state; a 409 applies the
 *    winning envelope so the caller converges.
 *  - **Send to PC** — [sendLink] relays a link to the paired desktops.
 *  - **Resilience** — transport failures disconnect and retry with the
 *    pure [backoffMillis] schedule.
 *
 * Pure Kotlin + coroutines only — unit-tested on a fake transport under
 * `testDebugUnitTest`, no Android APIs in the logic.
 */
class PhoneSyncEngine(
    private val transport: SyncTransport,
    private val credentials: SyncCredentialsStore,
    private val policy: PolicyRepository,
    private val onEvent: (SyncEngineEvent) -> Unit = {},
    private val onPolicyApplied: suspend () -> Unit = {},
    private val backoffMillis: (attempt: Int) -> Long = ::defaultBackoffMillis,
    private val pollIntervalMillis: Long = 30_000L,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val timeSource: () -> Long = System::currentTimeMillis,
) {

    private val pairingGate = CompletableDeferred<Unit>()

    fun start(scope: CoroutineScope): Job = scope.launch { runLoop() }

    internal suspend fun runLoop() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            if (credentials.load() == null) {
                onEvent(SyncEngineEvent.PairingRequired)
                pairingGate.await()
                continue
            }
            onEvent(SyncEngineEvent.Connecting)
            try {
                when (val pulled = transport.pull(trackedRevision().takeIf { it >= 0 })) {
                    is PullResult.Envelope -> apply(pulled.envelope)
                    PullResult.NotModified -> Unit
                    PullResult.Unauthorized -> {
                        onEvent(SyncEngineEvent.PairingRequired)
                        pairingGate.await()
                        continue
                    }
                }
                onEvent(SyncEngineEvent.Connected)
                attempt = 0
                pollLoop()
                onEvent(SyncEngineEvent.Disconnected("connection closed"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                onEvent(
                    SyncEngineEvent.Disconnected(failure.message ?: failure.javaClass.simpleName)
                )
            }
            sleeper(backoffMillis(attempt))
            attempt += 1
        }
    }

    /**
     * Conditional polling while connected: a 304 costs nothing, a fresh
     * envelope is applied to the domain. Any failure escapes to the run
     * loop's disconnect handling.
     */
    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            sleeper(pollIntervalMillis)
            when (val pulled = transport.pull(trackedRevision())) {
                is PullResult.Envelope -> if (pulled.envelope.revision > trackedRevision()) {
                    apply(pulled.envelope)
                }

                PullResult.NotModified -> Unit
                PullResult.Unauthorized -> error("unauthorized")
            }
        }
    }

    /**
     * Claims [code] for this phone, stores the credentials and releases
     * the pairing gate so the run loop continues with the fresh token.
     */
    suspend fun pair(code: String, deviceName: String = "phone"): PairingOutcome =
        when (val result = transport.claim(PairingClaim(code = code, deviceName = deviceName, deviceKind = "phone"))) {
            is PairingResult.Claimed -> {
                credentials.save(result.credentials)
                pairingGate.complete(Unit)
                PairingOutcome.PAIRED
            }
            PairingResult.Rejected -> PairingOutcome.REJECTED
        }

    /**
     * Conditional write of the current domain state. On a 409 the winning
     * server envelope is applied to the domain instead, so the caller
     * converges.
     */
    suspend fun pushCurrentState(hint: String? = null): PushResult {
        val deviceId = credentials.load()?.deviceId ?: "phone"
        val state: SyncState = policy.toSyncState(deviceId, timeSource())
        val update = StateUpdateRequest(baseRevision = trackedRevision().coerceAtLeast(0), state = state, hint = hint)
        return when (val result = transport.push(update)) {
            is PushResult.Accepted -> {
                apply(result.envelope)
                result
            }
            is PushResult.Conflict -> {
                apply(result.current)
                result
            }
            PushResult.BadRequest,
            PushResult.Unauthorized -> result
        }
    }

    /** "Send to PC": relays [url] to the paired desktops via the link endpoint. */
    suspend fun sendLink(url: String, title: String? = null): LinkSendResult {
        val payload = LinkPayload(
            id = UUID.randomUUID().toString(),
            url = url,
            title = title,
            sourceDeviceId = credentials.load()?.deviceId,
            sourceDeviceName = credentials.load()?.deviceName ?: "phone",
            sentAtMillis = timeSource(),
        )
        return transport.sendLink(payload)
    }

    private suspend fun apply(envelope: StateEnvelope) {
        if (trackedRevision() >= envelope.revision && trackedRevision() >= 0) return
        envelope.state.applyTo(policy, timeSource())
        credentials.setRevision(envelope.revision)
        onPolicyApplied()
    }

    private suspend fun trackedRevision(): Long = credentials.revision()

    companion object {
        /**
         * Pure reconnect schedule: exponential (base 1s, factor 2) capped at
         * 30s — deterministic in `attempt`, unit-tested without a clock.
         */
        fun defaultBackoffMillis(
            attempt: Int,
            baseMillis: Long = 1_000L,
            factor: Double = 2.0,
            maxMillis: Long = 30_000L,
        ): Long {
            if (attempt <= 0) return baseMillis
            val exponential = baseMillis * factor.pow(attempt)
            return min(exponential, maxMillis.toDouble()).toLong().coerceAtLeast(baseMillis)
        }
    }
}

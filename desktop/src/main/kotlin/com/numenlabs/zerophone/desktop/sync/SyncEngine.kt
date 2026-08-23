package com.numenlabs.zerophone.desktop.sync

import com.numenlabs.zerophone.core.model.PairingClaim
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.core.model.SyncState
import com.numenlabs.zerophone.desktop.state.SyncEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Result of a user-initiated pairing attempt. */
enum class PairingOutcome {
    /** The claim succeeded; credentials are stored and sync will start. */
    PAIRED,

    /** The shortcode was unknown, expired or already consumed. */
    REJECTED,
}

/**
 * Pure sync state machine over a [SyncTransport]:
 *
 *  - **Pairing gate** — with no stored device token the engine reports
 *    [SyncEvent.PairingRequired] and parks; [pair] claims a shortcode,
 *    stores the credentials and releases the gate. A rejected token on
 *    any call (401) re-enters the same state.
 *  - **Pull** — REST is the source of truth: a full pull on connect
 *    (conditional on the tracked revision, so an up-to-date client pays
 *    a 304 instead of a body) and exactly one re-pull per push noticed
 *    as ahead of the tracked revision. `state.snapshot` re-pulls on any
 *    revision mismatch, `state.updated` only when the revision moved
 *    ahead.
 *  - **Push** — conditional `PUT` from the tracked revision; a 409
 *    applies the winning envelope instead, so the caller converges.
 *  - **Resilience** — any transport failure emits
 *    [SyncEvent.Disconnected] and the loop retries after
 *    [ReconnectBackoff] (exponential, full jitter).
 *
 * The engine holds no Android/Compose/ktor types and is unit-tested
 * against a fake transport.
 */
class SyncEngine(
    private val transport: SyncTransport,
    private val tokenStore: DeviceTokenStore,
    private val onEvent: (SyncEvent) -> Unit,
    private val backoff: ReconnectBackoff = ReconnectBackoff(),
    private val sleeper: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {

    private val pairingGate = CompletableDeferred<Unit>()
    private val revisionGate = Any()

    /** Server revision this client has applied; -1 = never synced. */
    private var revision: Long = -1L

    fun start(scope: CoroutineScope): Job = scope.launch { runLoop() }

    internal suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            if (tokenStore.load() == null) {
                onEvent(SyncEvent.PairingRequired)
                pairingGate.await()
                continue
            }
            onEvent(SyncEvent.Connecting)
            try {
                when (val pulled = transport.pull(trackedRevision())) {
                    is PullResult.Envelope -> apply(pulled.envelope)
                    PullResult.NotModified -> Unit
                    PullResult.Unauthorized -> {
                        onEvent(SyncEvent.PairingRequired)
                        pairingGate.await()
                        continue
                    }
                }
                onEvent(SyncEvent.Connected)
                backoff.reset()
                listenForPushes()
                onEvent(SyncEvent.Disconnected("connection closed"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                onEvent(
                    SyncEvent.Disconnected(failure.message ?: failure.javaClass.simpleName)
                )
            }
            sleeper(backoff.nextDelayMillis())
        }
    }

    /**
     * Claims [code] for this device, stores the credentials and releases
     * the pairing gate so the run loop continues with the fresh token.
     */
    suspend fun pair(code: String, deviceName: String): PairingOutcome {
        val claim = PairingClaim(code = code, deviceName = deviceName)
        return when (val result = transport.claim(claim)) {
            is PairingResult.Claimed -> {
                tokenStore.save(result.credentials)
                pairingGate.complete(Unit)
                PairingOutcome.PAIRED
            }
            PairingResult.Rejected -> PairingOutcome.REJECTED
        }
    }

    /**
     * Conditional write of a locally edited [state]. On a 409 the winning
     * server envelope is applied instead, so the caller can rebase and
     * retry; returns the outcome for the caller to surface.
     */
    suspend fun push(state: SyncState, hint: String? = null): PushResult {
        val update = StateUpdateRequest(baseRevision = currentRevision(), state = state, hint = hint)
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

    private suspend fun listenForPushes() {
        transport.pushes().collect { push ->
            when (push) {
                is SyncPushMessage.StateSnapshot ->
                    if (push.revision != currentRevision()) reSync()

                is SyncPushMessage.StateUpdated ->
                    if (push.revision > currentRevision()) reSync()

                is SyncPushMessage.LinkReceived ->
                    onEvent(SyncEvent.LinkReceived(push.payload))
            }
        }
    }

    /** Exactly one conditional re-pull; a 304 means the push raced us. */
    private suspend fun reSync() {
        when (val pulled = transport.pull(trackedRevision())) {
            is PullResult.Envelope -> apply(pulled.envelope)
            PullResult.NotModified -> Unit
            PullResult.Unauthorized -> error("unauthorized")
        }
    }

    private fun apply(envelope: StateEnvelope) {
        synchronized(revisionGate) {
            if (revision >= 0 && revision >= envelope.revision) return
            revision = envelope.revision
        }
        onEvent(SyncEvent.SnapshotReceived(envelope.state.policy))
    }

    /** The tracked revision, or `null` before the first successful sync. */
    private fun trackedRevision(): Long? = synchronized(revisionGate) { revision }.takeIf { it >= 0 }

    private fun currentRevision(): Long = synchronized(revisionGate) { revision }
}

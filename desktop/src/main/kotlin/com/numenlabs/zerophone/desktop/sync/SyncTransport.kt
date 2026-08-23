package com.numenlabs.zerophone.desktop.sync

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.PairingClaim
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncPushMessage
import kotlinx.coroutines.flow.Flow

/**
 * Outcome of `GET /api/v1/state` (optionally conditional on a revision).
 *
 * - [Envelope] — 200 with the full state document and its revision;
 * - [NotModified] — 304: the client's `baseRevision` is still current;
 * - [Unauthorized] — 401: the device token is unknown or revoked and
 *   the device must pair again.
 */
sealed interface PullResult {
    data class Envelope(val envelope: StateEnvelope) : PullResult
    data object NotModified : PullResult
    data object Unauthorized : PullResult
}

/**
 * Outcome of the conditional `PUT /api/v1/state`.
 *
 * - [Accepted] — 200: the write landed, the carried revision is the new one;
 * - [Conflict] — 409: another writer was faster; the carried envelope is
 *   the current `{revision, state}` the losing writer must rebase onto;
 * - [BadRequest] — 400: protocol violation (baseRevision ahead of the
 *   server or a malformed document);
 * - [Unauthorized] — 401: pairing is required again.
 */
sealed interface PushResult {
    data class Accepted(val envelope: StateEnvelope) : PushResult
    data class Conflict(val current: StateEnvelope) : PushResult
    data object BadRequest : PushResult
    data object Unauthorized : PushResult
}

/** Outcome of claiming a pairing shortcode. */
sealed interface PairingResult {
    data class Claimed(val credentials: DeviceCredentials) : PairingResult

    /** Unknown/expired code (404), already consumed (409) or malformed (400). */
    data object Rejected : PairingResult
}

/**
 * Pure Kotlin view of the sync server: one pull endpoint with optional
 * revision conditionality, one conditional push, one pairing claim and a
 * push channel. No ktor/HTTP types leak through — [SyncEngine] depends
 * only on this interface, which is what the fake transport in the engine
 * tests implements. Transport-level failures (offline, DNS, timeouts)
 * surface as thrown exceptions and are handled by the engine's retry loop.
 */
interface SyncTransport {

    /**
     * `GET /api/v1/state`, with `?baseRevision=N` when [baseRevision] is
     * non-null — the server then answers 304 while `N` is still current.
     */
    suspend fun pull(baseRevision: Long? = null): PullResult

    /** `PUT /api/v1/state` with the optimistic-concurrency [update]. */
    suspend fun push(update: StateUpdateRequest): PushResult

    /** `POST /api/v1/pairing/claim` — exchange a shortcode for credentials. */
    suspend fun claim(claim: PairingClaim): PairingResult

    /**
     * `GET /api/v1/ws` as a cold flow of server pushes. The flow connects
     * on collection, emits [SyncPushMessage]s and completes (normally or
     * exceptionally) when the socket goes down — the engine treats any
     * completion as a disconnect and reconnects with backoff.
     */
    fun pushes(): Flow<SyncPushMessage>
}

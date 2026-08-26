package com.numenlabs.zerophone.core.data.sync

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.PairingClaim
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest

/**
 * Outcome of `GET /api/v1/state` (optionally conditional on a revision):
 * [Envelope] (200), [NotModified] (304), [Unauthorized] (401).
 */
sealed interface PullResult {
    data class Envelope(val envelope: StateEnvelope) : PullResult
    data object NotModified : PullResult
    data object Unauthorized : PullResult
}

/**
 * Outcome of the conditional `PUT /api/v1/state`: [Accepted] (200),
 * [Conflict] (409, carries the winning `{revision, state}`), [BadRequest]
 * (400), [Unauthorized] (401).
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
    data object Rejected : PairingResult
}

/**
 * Pure Kotlin view of the sync server for the phone: one pull endpoint
 * with optional revision conditionality, one conditional push and the
 * pairing claim. No ktor/HTTP types leak through —
 * [PhoneSyncEngine] depends only on this interface, which the fake
 * transport in the unit tests implements. Transport-level failures
 * (offline, DNS, timeouts) surface as thrown exceptions and are handled
 * by the engine's retry loop.
 */
interface SyncTransport {

    /** `GET /api/v1/state`, with `?baseRevision=N` answering 304 while current. */
    suspend fun pull(baseRevision: Long? = null): PullResult

    /** `PUT /api/v1/state` with the optimistic-concurrency [update]. */
    suspend fun push(update: StateUpdateRequest): PushResult

    /** `POST /api/v1/pairing/claim` — exchange a shortcode for credentials. */
    suspend fun claim(claim: PairingClaim): PairingResult
}

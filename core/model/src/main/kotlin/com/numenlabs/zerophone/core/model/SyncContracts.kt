package com.numenlabs.zerophone.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sync wire contracts shared by the phone, the desktop client and the sync
 * server (phase 3). This file is the single source of cross-device models:
 * pure Kotlin + kotlinx.serialization, no platform dependencies, no duplicates.
 */

/** REST/WebSocket paths of the self-hosted sync server. */
object SyncEndpoints {
    /** Push/pull of the synced state document (GET + PUT, conditional write). */
    const val STATE = "/api/v1/state"
    const val WEBSOCKET = "/api/v1/ws"
    const val PAIRING_CLAIM = "/api/v1/pairing/claim"

    /** "Send to PC": the phone POSTs a link, the server relays it to desktops. */
    const val LINK = "/api/v1/link"
    const val AUTH_ROTATE = "/api/v1/auth/rotate"
    const val AUTH_TOKEN = "/api/v1/auth/token"

    const val CURRENT_SCHEMA_VERSION = 1
}

/**
 * The ZeroPhone availability principle flattened to a wire enum: every
 * capability of the phone is in exactly one of these states. Mirrors the
 * engine states of `:core:context` (AvailabilityState) as a serializable DTO.
 */
@Serializable
enum class Availability {
    @SerialName("AVAILABLE") AVAILABLE,
    @SerialName("RESTRICTED") RESTRICTED,
    @SerialName("TEMPORARILY_AVAILABLE") TEMPORARILY_AVAILABLE,
    @SerialName("CONTEXTUAL") CONTEXTUAL,
    @SerialName("BLOCKED") BLOCKED,
}

/** What kind of capability a snapshot entry describes. */
@Serializable
enum class CapabilityKind {
    @SerialName("PACKAGE") PACKAGE,
    @SerialName("LOGICAL") LOGICAL,
}

/**
 * One capability/package of the phone with its resolved availability state.
 * `restrictionReason` / `contextCondition` / `remainingMillis` carry the
 * extra payload of the non-trivial states (null when not applicable).
 */
@Serializable
data class CapabilityAvailability(
    val id: String,
    val kind: CapabilityKind = CapabilityKind.PACKAGE,
    val label: String? = null,
    val state: Availability = Availability.BLOCKED,
    val restrictionReason: String? = null,
    val contextCondition: String? = null,
    val remainingMillis: Long? = null,
)

/**
 * Full policy snapshot of a device — the source of truth delivered by REST.
 * `emergencyRemainingMillis` uses the [EmergencyWindow] convention:
 * 0 means "no emergency window active".
 */
@Serializable
data class PolicySnapshot(
    val schemaVersion: Int = SyncEndpoints.CURRENT_SCHEMA_VERSION,
    val generatedAtMillis: Long = 0L,
    val deviceId: String = "",
    val deviceName: String? = null,
    val activeMode: String? = null,
    val emergencyRemainingMillis: Long = EmergencyWindow.NONE_DEADLINE,
    val capabilities: List<CapabilityAvailability> = emptyList(),
)

/**
 * The single synced state document — one revision for the whole phone
 * state, exactly like the on-device persistence is one document. The
 * [PolicySnapshot] stays the only policy schema; anything else the
 * devices sync later grows as new sections of this document.
 *
 * The revision is deliberately NOT part of [SyncState]: only the server
 * assigns revisions, and it stores them next to the serialized document
 * (see [StateEnvelope]).
 */
@Serializable
data class SyncState(
    val schemaVersion: Int = SyncEndpoints.CURRENT_SCHEMA_VERSION,
    val policy: PolicySnapshot = PolicySnapshot(),
)

/**
 * A state document together with its server-assigned revision.
 * Wire shape of `GET /api/v1/state` (200), of a successful PUT — and of
 * the 409 Conflict body, where it carries the revision and state that
 * beat the losing writer.
 *
 * `GET /api/v1/state?baseRevision=N` answers 304 Not Modified while `N`
 * still equals the current revision, so an up-to-date client pulls
 * nothing; otherwise it returns this envelope with 200.
 */
@Serializable
data class StateEnvelope(
    val state: SyncState = SyncState(),
    val revision: Long = 0L,
)

/**
 * Body of `PUT /api/v1/state`: optimistic-concurrency write of the whole
 * document. The server accepts it only while [baseRevision] still equals
 * its current revision:
 * - `baseRevision == current` → 200 with the new [StateEnvelope]
 *   (revision + 1) and a `state.updated` broadcast;
 * - `baseRevision < current` → 409 with the current [StateEnvelope]
 *   (`{revision, state}`) so the losing writer can rebase and retry;
 * - `baseRevision > current` → 400: the client knows a future the
 *   server does not, which is a protocol violation, not a conflict.
 *
 * [hint] is the writer's free-form hint of what changed, relayed to
 * other devices in the `state.updated` push.
 */
@Serializable
data class StateUpdateRequest(
    val baseRevision: Long,
    val state: SyncState,
    val hint: String? = null,
)

/** A link sent from the phone ("отправить на ПК") through the sync server. */
@Serializable
data class LinkPayload(
    val id: String,
    val url: String,
    val title: String? = null,
    val sourceDeviceId: String? = null,
    val sourceDeviceName: String? = null,
    val sentAtMillis: Long = 0L,
)

/**
 * WebSocket push envelope. The socket is only a notification channel:
 * the state variants carry revisions but never the state itself — a
 * client that is behind catches up with exactly one full REST pull of
 * `GET /api/v1/state` (no deltas, no replay). `link.received` is the
 * push half of send-to-PC and carries only the link payload.
 */
@Serializable
sealed interface SyncPushMessage {

    /**
     * Sent by the server immediately when the socket is established: the
     * revision the client is caught up to — or, after an offline period,
     * proof that it has fallen behind and must pull once.
     */
    @Serializable
    @SerialName("state.snapshot")
    data class StateSnapshot(val revision: Long) : SyncPushMessage

    /**
     * Broadcast after every accepted write: the new revision, the device
     * whose write won, and the writer's optional hint of what changed.
     */
    @Serializable
    @SerialName("state.updated")
    data class StateUpdated(
        val revision: Long,
        val actorDeviceId: String,
        val hint: String? = null,
    ) : SyncPushMessage

    @Serializable
    @SerialName("link.received")
    data class LinkReceived(val payload: LinkPayload) : SyncPushMessage
}

/** Request body for claiming a pairing code issued by the phone/server. */
@Serializable
data class PairingClaim(
    val code: String,
    val deviceName: String,
    val deviceKind: String = "desktop",
)

/**
 * Device credentials issued by the server after successful pairing.
 * The same shape is persisted locally by clients as the stored token file.
 */
@Serializable
data class DeviceCredentials(
    val deviceId: String,
    val token: String,
    val serverUrl: String? = null,
    val deviceName: String? = null,
    val pairedAtMillis: Long = 0L,
)

package com.numenlabs.zerophone.desktop.state

import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.PolicySnapshot

/**
 * Every input of the app state: sync-client lifecycle, incoming data and the
 * single local user action (explicitly opening an inbox item).
 */
sealed interface SyncEvent {
    /** No stored credentials (or the token was rejected): pairing is required. */
    data object PairingRequired : SyncEvent
    data object Connecting : SyncEvent
    data object Connected : SyncEvent
    data class Disconnected(val reason: String? = null) : SyncEvent
    data class SnapshotReceived(val snapshot: PolicySnapshot) : SyncEvent
    data class LinkReceived(val link: LinkPayload) : SyncEvent
    data class InboxItemOpened(val itemId: String) : SyncEvent
}

/** A link received from the phone, wrapped with desktop-local metadata. */
@kotlinx.serialization.Serializable
data class InboxItem(
    val link: LinkPayload,
    val receivedAtMillis: Long,
    val isOpened: Boolean = false,
) {
    val id: String get() = link.id
}

/**
 * Immutable app state of the desktop client. Policies and availability data
 * from `:core:model` are held as-is, without duplicates.
 */
data class DesktopAppState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val snapshot: PolicySnapshot? = null,
    val inbox: List<InboxItem> = emptyList(),
    val lastSyncAtMillis: Long? = null,
) {
    val unopenedCount: Int get() = inbox.count { !it.isOpened }

    companion object {
        /** Inbox is capped to keep the persisted JSON bounded; oldest items drop first. */
        const val MAX_INBOX_ITEMS = 200
    }
}

/**
 * Pure state reducer: (state, event, now) -> state. No side effects, no
 * Compose/coroutine dependencies — covered by unit tests.
 */
fun reduce(
    state: DesktopAppState,
    event: SyncEvent,
    nowMillis: Long = 0L,
): DesktopAppState = when (event) {
    SyncEvent.PairingRequired -> state.copy(connection = ConnectionState.NEEDS_PAIRING)

    SyncEvent.Connecting -> state.copy(connection = ConnectionState.CONNECTING)

    SyncEvent.Connected -> state.copy(connection = ConnectionState.CONNECTED)

    is SyncEvent.Disconnected -> state.copy(connection = ConnectionState.DISCONNECTED)

    is SyncEvent.SnapshotReceived -> state.copy(
        snapshot = event.snapshot,
        lastSyncAtMillis = nowMillis,
    )

    is SyncEvent.LinkReceived -> {
        if (state.inbox.any { it.id == event.link.id }) {
            state
        } else {
            val item = InboxItem(link = event.link, receivedAtMillis = nowMillis)
            state.copy(inbox = (listOf(item) + state.inbox).take(DesktopAppState.MAX_INBOX_ITEMS))
        }
    }

    is SyncEvent.InboxItemOpened -> state.copy(
        inbox = state.inbox.map {
            if (it.id == event.itemId) it.copy(isOpened = true) else it
        },
    )
}

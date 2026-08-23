package com.numenlabs.zerophone.desktop.state

import com.numenlabs.zerophone.desktop.sync.InboxStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the desktop UI: one [StateFlow] of one immutable
 * [DesktopAppState], updated exclusively through the pure [reduce] function.
 * The inbox is persisted to a local JSON file and survives restarts.
 */
class AppStateHolder(
    private val inboxStore: InboxStore? = null,
    private val timeSource: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(
        DesktopAppState(inbox = inboxStore?.load().orEmpty())
    )
    val state: StateFlow<DesktopAppState> = _state.asStateFlow()

    /** Entry point for sync-client events and local user actions. */
    fun dispatch(event: SyncEvent) {
        val before = _state.value
        val after = reduce(before, event, timeSource())
        if (after == before) return
        _state.value = after
        when (event) {
            is SyncEvent.LinkReceived,
            is SyncEvent.InboxItemOpened -> inboxStore?.save(after.inbox)
            else -> Unit
        }
    }

    /** Explicit user action: mark an inbox item as opened (persisted). */
    fun markOpened(itemId: String) = dispatch(SyncEvent.InboxItemOpened(itemId))
}

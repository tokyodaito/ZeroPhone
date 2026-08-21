package com.numenlabs.zerophone.core.data.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory snapshot of the currently-posted notifications, updated by
 * [ZeroNotificationListenerService] and read by the launcher UI. Pure Kotlin
 * state holder — the importance filter ([ImportantNotificationFilter]) decides
 * which of these count as "important unread".
 */
@Singleton
class ImportantNotificationsStore @Inject constructor() {

    private val _active = MutableStateFlow<List<ActiveNotification>>(emptyList())

    /** All currently-posted notifications (excluding ongoing ones), newest first. */
    val active: StateFlow<List<ActiveNotification>> = _active.asStateFlow()

    fun replaceAll(notifications: List<ActiveNotification>) {
        _active.value = notifications.sortedByDescending { it.postedAtMillis }
    }

    fun clear() {
        _active.value = emptyList()
    }
}

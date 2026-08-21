package com.numenlabs.zerophone.core.data.notifications

/**
 * Pure Kotlin importance filter for "важные непрочитанные" on the home screen.
 *
 * A notification counts as important when
 *  - its package is on the user's priority list (explicit choice), OR
 *  - its channel importance is at least [IMPORTANCE_HIGH] (high-attention
 *    channels: direct messages, calls, alarms — not silent feeds).
 *
 * The threshold constant mirrors android.app.NotificationManager.IMPORTANCE_HIGH
 * without importing Android types, keeping this class unit-testable on the JVM.
 */
class ImportantNotificationFilter(
    private val priorityPackages: Set<String> = emptySet(),
    private val minImportance: Int = IMPORTANCE_HIGH
) {

    fun isImportant(notification: ActiveNotification): Boolean =
        notification.packageName in priorityPackages ||
            notification.importance >= minImportance

    fun filter(notifications: List<ActiveNotification>): List<ActiveNotification> =
        notifications.filter(::isImportant)

    fun countImportant(notifications: List<ActiveNotification>): Int =
        notifications.count(::isImportant)

    companion object {
        /** Mirrors android.app.NotificationManager.IMPORTANCE_DEFAULT. */
        const val IMPORTANCE_DEFAULT: Int = 3

        /** Mirrors android.app.NotificationManager.IMPORTANCE_HIGH. */
        const val IMPORTANCE_HIGH: Int = 4

        /** Mirrors android.app.NotificationManager.IMPORTANCE_NONE. */
        const val IMPORTANCE_NONE: Int = 0
    }
}

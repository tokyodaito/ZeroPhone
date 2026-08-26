package com.numenlabs.zerophone.core.data.notifications

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps [ImportantNotificationsStore] in sync with the system notification
 * state. Enabled by the user in system notification-access settings; when not
 * enabled the launcher simply shows no unread counter (no permission prompt
 * from the app itself is possible for listener access).
 *
 * Ongoing notifications (media playback, navigation, calls in progress) are
 * excluded: they are not "unread messages requiring a reply".
 */
@AndroidEntryPoint
class ZeroNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var store: ImportantNotificationsStore

    private val active = LinkedHashMap<String, ActiveNotification>()

    override fun onListenerConnected() {
        active.clear()
        activeNotifications.forEach { sbn -> record(sbn) }
        publish()
    }

    override fun onListenerDisconnected() {
        active.clear()
        store.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        record(sbn)
        publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (active.remove(keyOf(sbn)) != null) publish()
    }

    private fun record(sbn: StatusBarNotification) {
        // Ongoing (media/navigation/calls) and group-summary notifications are
        // not "unread messages requiring a reply"; summaries would also double
        // count every group.
        if (sbn.isOngoing ||
            (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        ) {
            active.remove(keyOf(sbn))
            return
        }
        active[keyOf(sbn)] = ActiveNotification(
            key = keyOf(sbn),
            packageName = sbn.packageName,
            importance = importanceOf(sbn),
            postedAtMillis = sbn.postTime
        )
    }

    private fun publish() {
        store.replaceAll(active.values.toList())
    }

    private fun keyOf(sbn: StatusBarNotification): String =
        "${sbn.packageName}#${sbn.id}#${sbn.tag ?: ""}"

    /**
     * Channel importance (API 26+); pre-O notifications fall back to the
     * legacy priority mapping.
     *
     * The package-scoped two-arg [NotificationManager.getNotificationChannel]
     * is API 30+ — on API 26-29 only the one-arg overload exists and it sees
     * just our own package, so other apps' channels degrade to the legacy
     * mapping there (calling the two-arg form crashes with NoSuchMethodError).
     */
    private fun importanceOf(sbn: StatusBarNotification): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = sbn.notification.channelId
            if (channelId != null) {
                try {
                    val manager = getSystemService(NotificationManager::class.java)
                    val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        manager?.getNotificationChannel(sbn.packageName, channelId)
                    } else {
                        manager?.getNotificationChannel(channelId)
                    }
                    if (channel != null) return channel.importance
                } catch (_: Exception) {
                    // Fall through to the legacy mapping.
                }
            }
        }
        return legacyImportance(sbn.notification)
    }

    private fun legacyImportance(notification: Notification): Int = when {
        notification.priority >= Notification.PRIORITY_HIGH ->
            ImportantNotificationFilter.IMPORTANCE_HIGH
        notification.priority <= Notification.PRIORITY_LOW ->
            ImportantNotificationFilter.IMPORTANCE_NONE
        else -> ImportantNotificationFilter.IMPORTANCE_DEFAULT
    }
}

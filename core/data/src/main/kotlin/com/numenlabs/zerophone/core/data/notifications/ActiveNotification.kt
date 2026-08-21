package com.numenlabs.zerophone.core.data.notifications

/**
 * One currently-posted notification, reduced to what the importance filter
 * needs. Pure Kotlin — the Android listener maps StatusBarNotification into
 * this shape before filtering.
 */
data class ActiveNotification(
    val key: String,
    val packageName: String,
    val importance: Int,
    val postedAtMillis: Long
)

package com.numenlabs.zerophone.core.model

import kotlinx.serialization.Serializable

/**
 * The next calendar event shown on the home screen. Pure Kotlin domain type;
 * the Android implementation reads it from CalendarProvider (READ_CALENDAR)
 * and degrades to "no event" when the permission is missing or denied.
 */
@Serializable
data class CalendarEvent(
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean = false,
    val location: String? = null
) {
    init {
        require(endMillis >= beginMillis) { "end must not precede begin" }
    }

    /** True while [nowMillis] is inside the event's time span. */
    fun isHappening(nowMillis: Long): Boolean = beginMillis <= nowMillis && nowMillis < endMillis

    companion object {
        /** How far ahead [CalendarSource][com.numenlabs.zerophone.core.data.CalendarSource] looks for the next event. */
        const val DEFAULT_HORIZON_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}

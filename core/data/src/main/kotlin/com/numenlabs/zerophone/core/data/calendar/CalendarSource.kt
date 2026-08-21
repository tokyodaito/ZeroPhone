package com.numenlabs.zerophone.core.data.calendar

import com.numenlabs.zerophone.core.model.CalendarEvent

/**
 * Port supplying calendar context for the home screen and the contextual
 * engine. The Android implementation reads CalendarProvider; when READ_CALENDAR
 * is missing or denied it degrades to "no data" (null / false) — never crashes.
 */
interface CalendarSource {

    /** The next upcoming (or currently running) event within [horizonMillis], or null. */
    suspend fun nextEvent(
        nowMillis: Long,
        horizonMillis: Long = CalendarEvent.DEFAULT_HORIZON_MILLIS
    ): CalendarEvent?

    /** True while an event is happening right now (feeds CalendarBusy rules). */
    fun isBusy(nowMillis: Long): Boolean
}

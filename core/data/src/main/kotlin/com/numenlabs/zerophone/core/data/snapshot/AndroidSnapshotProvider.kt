package com.numenlabs.zerophone.core.data.snapshot

import com.numenlabs.zerophone.core.context.ContextSnapshot
import com.numenlabs.zerophone.core.context.SnapshotProvider
import com.numenlabs.zerophone.core.context.TimeBudgetLedger
import com.numenlabs.zerophone.core.context.WeekDay
import com.numenlabs.zerophone.core.data.calendar.CalendarSource
import java.util.Calendar

/**
 * Android [SnapshotProvider]: local time-of-day / day-of-week / epoch day
 * (device timezone) and calendar-busy state from [CalendarSource].
 * java.util.Calendar (not java.time) keeps it usable on minSdk 24 without
 * desugaring.
 */
class AndroidSnapshotProvider(
    private val calendarSource: CalendarSource
) : SnapshotProvider {

    override fun snapshot(nowMillis: Long, activeMode: String?): ContextSnapshot {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMillis
        return ContextSnapshot(
            nowMillis = nowMillis,
            minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE),
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK).toWeekDay(),
            activeMode = activeMode,
            calendarBusy = calendarSource.isBusy(nowMillis),
            epochDay = TimeBudgetLedger.epochDayOf(nowMillis, calendar.utcOffsetMillis())
        )
    }

    /** Total zone offset (DST included) of this calendar, in ms east of UTC. */
    private fun Calendar.utcOffsetMillis(): Long =
        (get(Calendar.ZONE_OFFSET) + get(Calendar.DST_OFFSET)).toLong()

    private fun Int.toWeekDay(): WeekDay = when (this) {
        Calendar.MONDAY -> WeekDay.MONDAY
        Calendar.TUESDAY -> WeekDay.TUESDAY
        Calendar.WEDNESDAY -> WeekDay.WEDNESDAY
        Calendar.THURSDAY -> WeekDay.THURSDAY
        Calendar.FRIDAY -> WeekDay.FRIDAY
        Calendar.SATURDAY -> WeekDay.SATURDAY
        Calendar.SUNDAY -> WeekDay.SUNDAY
        else -> WeekDay.MONDAY
    }
}

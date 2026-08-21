package com.numenlabs.zerophone.core.context

import kotlinx.serialization.Serializable

/**
 * Days of the week used by [RuleCondition.TimeWindow]. Own enum (not java.time)
 * so the engine stays usable on minSdk 24 without desugaring.
 */
@Serializable
enum class WeekDay {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    /** The day before this one (wraps around the week). */
    fun previous(): WeekDay = entries[(ordinal + entries.size - 1) % entries.size]

    companion object {
        val ALL: Set<WeekDay> = entries.toSet()
        val WEEKDAYS: Set<WeekDay> = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
        val WEEKEND: Set<WeekDay> = setOf(SATURDAY, SUNDAY)
    }
}

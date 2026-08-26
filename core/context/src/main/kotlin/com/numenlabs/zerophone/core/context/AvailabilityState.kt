package com.numenlabs.zerophone.core.context

import kotlinx.serialization.Serializable

/**
 * The ZeroPhone core principle as a domain type: every capability of the phone
 * is always in exactly one of these states.
 */
sealed interface AvailabilityState {

    /** Fully available to the user. */
    object Available : AvailabilityState

    /** Available with restrictions (time-limited usage, reduced functionality). */
    data class Restricted(val reason: RestrictionReason) : AvailabilityState

    /** Temporarily available; will fall back automatically when the grant expires. */
    data class TemporarilyAvailable(val remainingMillis: Long) : AvailabilityState

    /** Available only while the given [ContextSignal]s / rule condition hold true. */
    data class Contextual(
        val requiredSignals: Set<ContextSignal> = emptySet(),
        val condition: String = ""
    ) : AvailabilityState

    /** Fully blocked. */
    object Blocked : AvailabilityState
}

@Serializable
enum class RestrictionReason { TIME_BUDGET, SUPERVISOR, FOCUS_MODE }

enum class ContextSignal { AT_WORK, AT_HOME, CALENDAR_EVENT_ACTIVE, NIGHT_TIME, CHARGING }

/**
 * Everything the engine knows about "now": wall clock, local time-of-day for
 * TimeWindow rules, the active mode and whether a calendar event is running.
 * minuteOfDay/dayOfWeek/epochDay are supplied by the caller (local timezone)
 * so the engine itself stays pure and timezone-free; the epoch day default is
 * UTC-derived for tests and pure-Kotlin callers without a zone.
 */
data class ContextSnapshot(
    val nowMillis: Long = 0L,
    val minuteOfDay: Int = 0,
    val dayOfWeek: WeekDay = WeekDay.MONDAY,
    val activeMode: String? = null,
    val calendarBusy: Boolean = false,
    val activeSignals: Set<ContextSignal> = emptySet(),
    val epochDay: Long = TimeBudgetLedger.epochDayOf(nowMillis)
)

/** Stable mode identifiers used by rules, persistence and the launcher UI. */
object ModeIds {
    const val WORK = "work"
    const val REST = "rest"
    const val FOCUS = "focus"

    val ALL = listOf(WORK, REST, FOCUS)
}

/**
 * Contract for evaluating a single capability against the current situation.
 * The phase-2 implementation is [RuleEngine]; [SignalContextEngine] remains
 * as the minimal placeholder contract implementation.
 */
interface ContextEngine {
    fun evaluate(snapshot: ContextSnapshot): AvailabilityState
}

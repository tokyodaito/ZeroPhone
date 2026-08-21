package com.numenlabs.zerophone.core.context

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

    /** Available only while the given [ContextSignal]s hold true. */
    data class Contextual(val requiredSignals: Set<ContextSignal>) : AvailabilityState

    /** Fully blocked. */
    object Blocked : AvailabilityState
}

enum class RestrictionReason { TIME_BUDGET, SUPERVISOR, FOCUS_MODE }

enum class ContextSignal { AT_WORK, AT_HOME, CALENDAR_EVENT_ACTIVE, NIGHT_TIME, CHARGING }

/**
 * Input for the contextual engine (phase 2): what is known about the user's
 * current situation plus the static rules from configuration.
 */
data class ContextSnapshot(
    val nowMillis: Long = 0L,
    val activeSignals: Set<ContextSignal> = emptySet()
)

/**
 * Contract for the contextual availability engine. The implementation arrives in
 * phase 2; UI and policy code should depend only on this interface.
 */
interface ContextEngine {
    fun evaluate(snapshot: ContextSnapshot): AvailabilityState
}

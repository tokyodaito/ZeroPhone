package com.numenlabs.zerophone.core.model

/**
 * Pure Kotlin evaluation of the persisted emergency-unlock deadline (unit-testable).
 *
 * NONE_DEADLINE (0) means "no emergency window persisted".
 * A deadline that has already passed evaluates to [Expired] so callers can catch up
 * (re-apply the suspend policy immediately) — this is the persisted-deadline catch-up rule.
 */
sealed class EmergencyWindow {

    object None : EmergencyWindow()
    object Expired : EmergencyWindow()
    data class Active(val remainingMillis: Long) : EmergencyWindow()

    companion object {
        const val NONE_DEADLINE: Long = 0L
        const val DEFAULT_DURATION_MS: Long = 30L * 60L * 1000L

        fun evaluate(deadlineMillis: Long, nowMillis: Long): EmergencyWindow = when {
            deadlineMillis <= NONE_DEADLINE -> None
            deadlineMillis <= nowMillis -> Expired
            else -> Active(deadlineMillis - nowMillis)
        }
    }
}

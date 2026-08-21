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

        /** Preset emergency-window durations (minutes) offered by the launcher. */
        val PRESET_MINUTES: List<Long> = listOf(5L, 15L, 30L, 60L)

        const val MIN_DURATION_MS: Long = 1L * 60L * 1000L
        const val MAX_DURATION_MS: Long = 12L * 60L * 60L * 1000L

        /** Clamps a custom duration into the supported range. */
        fun sanitizeDurationMillis(durationMillis: Long): Long =
            durationMillis.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)

        fun evaluate(deadlineMillis: Long, nowMillis: Long): EmergencyWindow = when {
            deadlineMillis <= NONE_DEADLINE -> None
            deadlineMillis <= nowMillis -> Expired
            else -> Active(deadlineMillis - nowMillis)
        }
    }
}

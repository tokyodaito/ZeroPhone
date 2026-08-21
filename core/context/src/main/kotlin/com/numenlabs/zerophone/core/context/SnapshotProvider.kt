package com.numenlabs.zerophone.core.context

/**
 * Port supplying the engine's [ContextSnapshot] from platform sources:
 * local timezone (minute of day / day of week), calendar-busy state and the
 * active mode. The Android implementation lives in :core:data (phase 2,
 * data-source PR); [None] is the safe fallback used by tests and by callers
 * without data sources.
 */
interface SnapshotProvider {

    fun snapshot(nowMillis: Long, activeMode: String?): ContextSnapshot

    object None : SnapshotProvider {
        override fun snapshot(nowMillis: Long, activeMode: String?): ContextSnapshot =
            ContextSnapshot(
                nowMillis = nowMillis,
                activeMode = activeMode,
                calendarBusy = false
            )
    }
}

package com.numenlabs.zerophone.core.context

import kotlinx.serialization.Serializable

/**
 * Pure Kotlin daily time-budget ledger for LIMITED (restricted) capabilities.
 * Usage is tracked per capability id per epoch day; a new day starts a fresh
 * ledger. The engine is timezone-free: callers pass the day (normally the
 * device-local epoch day from [ContextSnapshot]). [TimeBudgetLedger.withUsage]
 * collapses to a single day so the persisted structure never grows.
 */
@Serializable
data class TimeBudgetLedger(
    val epochDay: Long = NONE,
    val usedMillis: Map<String, Long> = emptyMap()
) {

    fun usedFor(capabilityId: String, onEpochDay: Long): Long =
        if (epochDay == onEpochDay) usedMillis[capabilityId] ?: 0L else 0L

    fun isExhausted(capabilityId: String, onEpochDay: Long, budgetMillis: Long): Boolean =
        usedFor(capabilityId, onEpochDay) >= budgetMillis

    /** Returns a ledger for [onEpochDay] with [millis] more usage recorded. */
    fun withUsage(capabilityId: String, onEpochDay: Long, millis: Long): TimeBudgetLedger {
        val base = if (epochDay == onEpochDay) usedMillis else emptyMap()
        val merged = base.toMutableMap()
        merged[capabilityId] = (merged[capabilityId] ?: 0L) + millis
        return TimeBudgetLedger(epochDay = onEpochDay, usedMillis = merged)
    }

    /**
     * Returns a ledger for [onEpochDay] with the usage of [capabilityId] set
     * to exactly [millis] (idempotent absolute write — callers re-measure the
     * whole day on every pass instead of accumulating deltas).
     */
    fun withAbsoluteUsage(capabilityId: String, onEpochDay: Long, millis: Long): TimeBudgetLedger {
        val base = if (epochDay == onEpochDay) usedMillis else emptyMap()
        return TimeBudgetLedger(epochDay = onEpochDay, usedMillis = base + (capabilityId to millis))
    }

    companion object {
        const val NONE: Long = -1L

        /** UTC epoch day of [nowMillis]. */
        fun epochDayOf(nowMillis: Long): Long = epochDayOf(nowMillis, utcOffsetMillis = 0L)

        /**
         * Local epoch day of [nowMillis] for a zone with the given total UTC
         * offset (DST included), so budget days end at local midnight rather
         * than 00:00 UTC.
         */
        fun epochDayOf(nowMillis: Long, utcOffsetMillis: Long): Long =
            Math.floorDiv(nowMillis + utcOffsetMillis, MILLIS_PER_DAY)

        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}

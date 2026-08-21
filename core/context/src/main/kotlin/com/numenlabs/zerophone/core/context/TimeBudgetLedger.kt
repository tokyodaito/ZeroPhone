package com.numenlabs.zerophone.core.context

import kotlinx.serialization.Serializable

/**
 * Pure Kotlin daily time-budget ledger for LIMITED (restricted) capabilities.
 * Usage is tracked per capability id per UTC epoch day; a new day starts a
 * fresh ledger. [TimeBudgetLedger.withUsage] collapses to a single day so the
 * persisted structure never grows.
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

    companion object {
        const val NONE: Long = -1L

        fun epochDayOf(nowMillis: Long): Long = Math.floorDiv(nowMillis, MILLIS_PER_DAY)

        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}

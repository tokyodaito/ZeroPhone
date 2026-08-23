package com.numenlabs.zerophone.desktop.sync

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential backoff with full jitter for reconnects/retries:
 * base 1s, factor 2, cap 30s. The next delay is drawn uniformly from
 * [0, ceiling) where ceiling doubles per attempt up to the cap — pure and
 * unit-testable, `Random` is injected.
 */
class ReconnectBackoff(
    private val baseMillis: Long = DEFAULT_BASE_MILLIS,
    private val factor: Double = DEFAULT_FACTOR,
    private val maxMillis: Long = DEFAULT_MAX_MILLIS,
) {
    private var attempt: Int = 0

    val attempts: Int get() = attempt

    fun nextDelayMillis(random: Random = Random.Default): Long {
        val ceiling = ceilingMillis(attempt)
        attempt += 1
        return fullJitter(ceiling, random)
    }

    fun reset() {
        attempt = 0
    }

    fun ceilingMillis(attemptIndex: Int): Long {
        if (attemptIndex <= 0) return baseMillis.coerceAtMost(maxMillis)
        val exponential = baseMillis * factor.pow(attemptIndex)
        return min(exponential, maxMillis.toDouble()).toLong().coerceAtLeast(baseMillis)
    }

    private fun fullJitter(ceiling: Long, random: Random): Long =
        if (ceiling <= 0L) 0L else (random.nextDouble() * ceiling).toLong()

    companion object {
        const val DEFAULT_BASE_MILLIS = 1_000L
        const val DEFAULT_FACTOR = 2.0
        const val DEFAULT_MAX_MILLIS = 30_000L
    }
}

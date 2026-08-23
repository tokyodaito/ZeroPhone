package com.numenlabs.zerophone.desktop.sync

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectBackoffTest {

    @Test
    fun `ceiling grows exponentially from base with factor 2`() {
        val backoff = ReconnectBackoff()
        assertEquals(1_000L, backoff.ceilingMillis(0))
        assertEquals(2_000L, backoff.ceilingMillis(1))
        assertEquals(4_000L, backoff.ceilingMillis(2))
        assertEquals(8_000L, backoff.ceilingMillis(3))
        assertEquals(16_000L, backoff.ceilingMillis(4))
    }

    @Test
    fun `ceiling is capped at max`() {
        val backoff = ReconnectBackoff()
        assertEquals(30_000L, backoff.ceilingMillis(5))
        assertEquals(30_000L, backoff.ceilingMillis(10))
        assertEquals(30_000L, backoff.ceilingMillis(100))
    }

    @Test
    fun `full jitter stays within 0 until ceiling`() {
        repeat(8) { attempt ->
            val ceiling = ReconnectBackoff().ceilingMillis(attempt)
            repeat(50) { draw ->
                // A fresh backoff advanced to the given attempt level, so
                // every draw is compared against the ceiling it was drawn
                // from — the shared-instance version compared draws from
                // later attempt levels against an earlier ceiling.
                val backoff = ReconnectBackoff()
                repeat(attempt) { backoff.nextDelayMillis(Random(draw)) }
                val delay = backoff.nextDelayMillis(Random(attempt * 1000 + draw))
                assertTrue("delay=$delay not in [0, $ceiling)", delay in 0 until ceiling)
            }
        }
    }

    @Test
    fun `attempts advance and reset restarts the sequence`() {
        val backoff = ReconnectBackoff()
        backoff.nextDelayMillis(Random(1))
        backoff.nextDelayMillis(Random(2))
        assertEquals(2, backoff.attempts)
        backoff.reset()
        assertEquals(0, backoff.attempts)
        assertEquals(1_000L, backoff.ceilingMillis(backoff.attempts))
    }

    @Test
    fun `custom parameters are respected`() {
        val backoff = ReconnectBackoff(baseMillis = 100, factor = 3.0, maxMillis = 5_000)
        assertEquals(100L, backoff.ceilingMillis(0))
        assertEquals(300L, backoff.ceilingMillis(1))
        assertEquals(900L, backoff.ceilingMillis(2))
        assertEquals(2_700L, backoff.ceilingMillis(3))
        assertEquals(5_000L, backoff.ceilingMillis(4))
    }
}

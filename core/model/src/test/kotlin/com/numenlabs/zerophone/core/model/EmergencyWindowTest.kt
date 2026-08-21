package com.numenlabs.zerophone.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyWindowTest {

    @Test
    fun `zero deadline means no window`() {
        assertEquals(EmergencyWindow.None, EmergencyWindow.evaluate(EmergencyWindow.NONE_DEADLINE, 1_000L))
    }

    @Test
    fun `negative deadline is treated as no window`() {
        assertEquals(EmergencyWindow.None, EmergencyWindow.evaluate(-5L, 1_000L))
    }

    @Test
    fun `future deadline is active with exact remaining time`() {
        val result = EmergencyWindow.evaluate(deadlineMillis = 10_000L, nowMillis = 4_000L)
        assertTrue(result is EmergencyWindow.Active)
        assertEquals(6_000L, (result as EmergencyWindow.Active).remainingMillis)
    }

    @Test
    fun `deadline equal to now is expired`() {
        assertEquals(EmergencyWindow.Expired, EmergencyWindow.evaluate(10_000L, 10_000L))
    }

    @Test
    fun `deadline in the past is expired`() {
        assertEquals(EmergencyWindow.Expired, EmergencyWindow.evaluate(10_000L, 10_500L))
    }

    @Test
    fun `default duration is thirty minutes`() {
        assertEquals(30L * 60L * 1000L, EmergencyWindow.DEFAULT_DURATION_MS)
    }
}

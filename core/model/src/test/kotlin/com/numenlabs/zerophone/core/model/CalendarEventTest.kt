package com.numenlabs.zerophone.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventTest {

    @Test
    fun `isHappening is inclusive of begin and exclusive of end`() {
        val event = CalendarEvent("Daily", beginMillis = 100L, endMillis = 200L)
        assertFalse(event.isHappening(99L))
        assertTrue(event.isHappening(100L))
        assertTrue(event.isHappening(199L))
        assertFalse(event.isHappening(200L))
    }

    @Test
    fun `end before begin is rejected`() {
        try {
            CalendarEvent("Broken", beginMillis = 200L, endMillis = 100L)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun `default horizon is one day`() {
        assertEquals(24L * 60L * 60L * 1000L, CalendarEvent.DEFAULT_HORIZON_MILLIS)
    }
}

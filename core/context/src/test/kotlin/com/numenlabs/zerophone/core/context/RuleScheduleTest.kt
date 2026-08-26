package com.numenlabs.zerophone.core.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleScheduleTest {

    private fun timeWindowRule(startMinute: Int, endMinute: Int) = Rule(
        "r",
        RuleTarget.Logical(LogicalCapabilities.CALL),
        RuleCondition.TimeWindow(startMinute, endMinute),
        RuleDecision.Block
    )

    @Test
    fun `no time window rules yields no boundary`() {
        val rule = Rule(
            "always",
            RuleTarget.All,
            RuleCondition.Always,
            RuleDecision.Allow
        )
        assertNull(RuleSchedule.nextBoundaryMillis(listOf(rule), nowMillis = 1_000L, utcOffsetMillis = 0L))
        assertNull(RuleSchedule.nextBoundaryMillis(emptyList(), nowMillis = 1_000L, utcOffsetMillis = 0L))
    }

    @Test
    fun `next boundary is the nearest upcoming start or end in utc zone`() {
        // 03:00 UTC on day 1 == minute 180.
        val now = MILLIS_PER_DAY + 3L * 60 * 60 * 1000
        val rule = timeWindowRule(startMinute = 22 * 60, endMinute = 23 * 60)
        val next = RuleSchedule.nextBoundaryMillis(listOf(rule), now, utcOffsetMillis = 0L)
        assertEquals(MILLIS_PER_DAY + 22L * 60 * 60 * 1000, next)
    }

    @Test
    fun `passed boundary rolls to tomorrow`() {
        // 23:30 UTC on day 1; window 22:00-23:00 — both boundaries passed.
        val now = MILLIS_PER_DAY + 23L * 60 * 60 * 1000 + 30 * 60 * 1000
        val rule = timeWindowRule(startMinute = 22 * 60, endMinute = 23 * 60)
        val next = RuleSchedule.nextBoundaryMillis(listOf(rule), now, utcOffsetMillis = 0L)
        // Nearest tomorrow boundary is the 22:00 start.
        assertEquals(2 * MILLIS_PER_DAY + 22L * 60 * 60 * 1000, next)
    }

    @Test
    fun `utc offset shifts the boundary by the offset`() {
        // Local UTC+3: local 22:00 == UTC 19:00 on the same day.
        val now = MILLIS_PER_DAY + 10L * 60 * 60 * 1000 // 10:00 UTC == 13:00 local
        val rule = timeWindowRule(startMinute = 22 * 60, endMinute = 23 * 60)
        val next = RuleSchedule.nextBoundaryMillis(listOf(rule), now, utcOffsetMillis = 3L * 60 * 60 * 1000)
        assertEquals(MILLIS_PER_DAY + 19L * 60 * 60 * 1000, next)
    }

    @Test
    fun `nearest of several rules wins`() {
        val now = MILLIS_PER_DAY + 10L * 60 * 60 * 1000
        val rules = listOf(
            timeWindowRule(startMinute = 20 * 60, endMinute = 21 * 60),
            timeWindowRule(startMinute = 12 * 60, endMinute = 18 * 60)
        )
        val next = RuleSchedule.nextBoundaryMillis(rules, now, utcOffsetMillis = 0L)
        assertEquals(MILLIS_PER_DAY + 12L * 60 * 60 * 1000, next)
    }

    @Test
    fun `boundary across a DST switch uses the offset at the boundary`() {
        // Scheduling on Saturday at +1h; the Sunday 07:00 boundary falls into
        // the +2h period. The rough candidate (07:00 wallclock - 1h) fires an
        // hour late; the refined one lands exactly on local 07:00.
        val saturday = MILLIS_PER_DAY + 10L * 60 * 60 * 1000 - 1L * 60 * 60 * 1000 // 09:00 UTC = 10:00 +1
        val rule = timeWindowRule(startMinute = 7 * 60, endMinute = 8 * 60)
        val next = RuleSchedule.nextBoundaryMillis(
            listOf(rule),
            saturday,
            utcOffsetMillis = 1L * 60 * 60 * 1000,
            offsetAtMillis = { millis -> if (millis >= MILLIS_PER_DAY) 2L * 60 * 60 * 1000 else 1L * 60 * 60 * 1000 }
        )
        // Local 07:00 next day at +2 == 05:00 UTC on day 2.
        assertEquals(2 * MILLIS_PER_DAY + 5L * 60 * 60 * 1000, next)
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}

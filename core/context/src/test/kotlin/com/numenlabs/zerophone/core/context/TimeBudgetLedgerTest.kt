package com.numenlabs.zerophone.core.context

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeBudgetLedgerTest {

    @Test
    fun `utc epoch day matches day boundaries at midnight UTC`() {
        assertEquals(0L, TimeBudgetLedger.epochDayOf(0L))
        assertEquals(0L, TimeBudgetLedger.epochDayOf(86_400_000L - 1L))
        assertEquals(1L, TimeBudgetLedger.epochDayOf(86_400_000L))
    }

    @Test
    fun `local epoch day rolls over at local midnight`() {
        val plusThreeHours = 3 * 60 * 60 * 1000L

        // 21:00 UTC is already the next local day at UTC+3.
        assertEquals(
            1L,
            TimeBudgetLedger.epochDayOf(86_400_000L - 3 * 60 * 60 * 1000L, plusThreeHours)
        )
        // ...but still the same UTC day.
        assertEquals(
            0L,
            TimeBudgetLedger.epochDayOf(86_400_000L - 3 * 60 * 60 * 1000L)
        )

        // Negative offsets push the local day back instead.
        val minusThreeHours = -3 * 60 * 60 * 1000L
        assertEquals(
            -1L, // 02:00 UTC == 23:00 of the previous local day
            TimeBudgetLedger.epochDayOf(2 * 60 * 60 * 1000L, minusThreeHours)
        )
    }

    @Test
    fun `withUsage collapses to a single day and keeps per-capability totals`() {
        val day = TimeBudgetLedger.epochDayOf(86_400_000L)
        val ledger = TimeBudgetLedger()
            .withUsage("game.app", day, 60_000L)
            .withUsage("game.app", day, 30_000L)
            .withUsage("video.app", day, 10_000L)

        assertEquals(90_000L, ledger.usedFor("game.app", day))
        assertEquals(10_000L, ledger.usedFor("video.app", day))
        assertEquals(mapOf("game.app" to 90_000L, "video.app" to 10_000L), ledger.usedMillis)

        // A new day starts fresh.
        assertEquals(0L, ledger.usedFor("game.app", day + 1))
    }
}

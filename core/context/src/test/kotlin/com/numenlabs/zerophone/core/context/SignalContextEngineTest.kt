package com.numenlabs.zerophone.core.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalContextEngineTest {

    private val engine = SignalContextEngine

    @Test
    fun `no signals means blocked - safest default`() {
        assertEquals(AvailabilityState.Blocked, engine.evaluate(ContextSnapshot()))
    }

    @Test
    fun `active signals grant contextual availability`() {
        val state = engine.evaluate(
            ContextSnapshot(activeSignals = setOf(ContextSignal.AT_HOME, ContextSignal.CHARGING))
        )
        assertTrue(state is AvailabilityState.Contextual)
        assertEquals(setOf(ContextSignal.AT_HOME, ContextSignal.CHARGING), (state as AvailabilityState.Contextual).requiredSignals)
    }

    @Test
    fun `temporarily available carries remaining time`() {
        val state = AvailabilityState.TemporarilyAvailable(remainingMillis = 90_000L)
        assertEquals(90_000L, state.remainingMillis)
    }
}

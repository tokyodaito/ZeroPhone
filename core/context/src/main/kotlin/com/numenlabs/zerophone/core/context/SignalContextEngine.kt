package com.numenlabs.zerophone.core.context

/**
 * Placeholder deterministic engine used until the phase-2 contextual engine
 * replaces it. Keeps the contract executable and testable: with no active
 * signals everything stays [AvailabilityState.Blocked] — the safest default
 * (matches the ZeroPhone principle "blocked unless explicitly granted").
 */
object SignalContextEngine : ContextEngine {
    override fun evaluate(snapshot: ContextSnapshot): AvailabilityState =
        if (snapshot.activeSignals.isEmpty()) AvailabilityState.Blocked
        else AvailabilityState.Contextual(requiredSignals = snapshot.activeSignals)
}

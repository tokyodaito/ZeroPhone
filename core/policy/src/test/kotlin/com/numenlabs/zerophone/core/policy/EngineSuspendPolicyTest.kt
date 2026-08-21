package com.numenlabs.zerophone.core.policy

import com.numenlabs.zerophone.core.context.AvailabilityState
import com.numenlabs.zerophone.core.context.CapabilityRef
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure mapping from engine states to the suspend set: only an explicit BLOCKED
 * inside the guarded candidate set is suspended; every other state stays
 * runnable; candidates without a state (defensive) are never suspended.
 */
class EngineSuspendPolicyTest {

    @Test
    fun `only blocked candidates are suspended`() {
        val candidates = setOf("blocked.app", "available.app", "restricted.app", "temporary.app", "contextual.app")
        val states: Map<CapabilityRef, AvailabilityState> = mapOf(
            CapabilityRef.Package("blocked.app") to AvailabilityState.Blocked,
            CapabilityRef.Package("available.app") to AvailabilityState.Available,
            CapabilityRef.Package("restricted.app") to AvailabilityState.Restricted(
                com.numenlabs.zerophone.core.context.RestrictionReason.TIME_BUDGET
            ),
            CapabilityRef.Package("temporary.app") to AvailabilityState.TemporarilyAvailable(60_000L),
            CapabilityRef.Package("contextual.app") to AvailabilityState.Contextual()
        )
        assertEquals(setOf("blocked.app"), EngineSuspendPolicy.computeSuspendSet(candidates, states))
    }

    @Test
    fun `candidate without a resolved state is never suspended`() {
        val candidates = setOf("unknown.app")
        assertEquals(emptySet<String>(), EngineSuspendPolicy.computeSuspendSet(candidates, emptyMap()))
    }

    @Test
    fun `guarded candidates filtered by SuspendPolicy stay safe even if the engine blocks them`() {
        // SuspendPolicy filter runs before the engine in PolicyApplier — verify the
        // invariant directly: protected/self/allowlisted never reach the mapper.
        val launchable = setOf("com.numenlabs.zerophone", "com.android.settings", "allowed.app", "game.app")
        val candidates = SuspendPolicy.computeSuspendSet(
            selfPackage = "com.numenlabs.zerophone",
            launchablePackages = launchable,
            allowlist = setOf("allowed.app"),
            protectedPackages = SuspendPolicy.DEFAULT_PROTECTED_PACKAGES,
            protectedPrefixes = SuspendPolicy.DEFAULT_PROTECTED_PREFIXES
        )
        assertEquals(setOf("game.app"), candidates)
        val engineBlocksEverything: Map<CapabilityRef, AvailabilityState> =
            candidates.associate { CapabilityRef.Package(it) to AvailabilityState.Blocked }
        assertEquals(setOf("game.app"), EngineSuspendPolicy.computeSuspendSet(candidates, engineBlocksEverything))
    }
}

package com.numenlabs.zerophone.core.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the seed invariants that keep ModeCatalog safe: seeds never widen
 * package defaults, mode restrictions override the baseline only while their
 * mode is active, and rule ids stay unique.
 */
class ModeCatalogTest {

    @Test
    fun `seed rules never target packages so allowlist defaults are untouched`() {
        ModeCatalog.seedRules().forEach { rule ->
            assertTrue(
                "seed rule ${rule.id} must target a logical capability",
                rule.target is RuleTarget.Logical
            )
        }
    }

    @Test
    fun `seed rule ids are unique`() {
        val ids = ModeCatalog.seedRules().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `baseline makes every quick-action capability available`() {
        val environment = EvaluationEnvironment(rules = ModeCatalog.seedRules())
        LogicalCapabilities.ALL.forEach { capability ->
            assertEquals(
                capability,
                AvailabilityState.Available,
                RuleEngine.evaluate(environment, CapabilityRef.Logical(capability))
            )
        }
    }

    @Test
    fun `focus mode restricts call message and camera while active`() {
        val environment = EvaluationEnvironment(
            rules = ModeCatalog.seedRules(),
            snapshot = ContextSnapshot(activeMode = ModeIds.FOCUS)
        )
        assertEquals(
            AvailabilityState.Restricted(RestrictionReason.FOCUS_MODE),
            RuleEngine.evaluate(environment, CapabilityRef.Logical(LogicalCapabilities.CALL))
        )
        assertEquals(
            AvailabilityState.Restricted(RestrictionReason.FOCUS_MODE),
            RuleEngine.evaluate(environment, CapabilityRef.Logical(LogicalCapabilities.MESSAGE))
        )
        assertEquals(
            AvailabilityState.Restricted(RestrictionReason.FOCUS_MODE),
            RuleEngine.evaluate(environment, CapabilityRef.Logical(LogicalCapabilities.CAMERA))
        )
        // Navigate/pay stay available in focus mode.
        assertEquals(
            AvailabilityState.Available,
            RuleEngine.evaluate(environment, CapabilityRef.Logical(LogicalCapabilities.NAVIGATE))
        )
        assertEquals(
            AvailabilityState.Available,
            RuleEngine.evaluate(environment, CapabilityRef.Logical(LogicalCapabilities.PAY))
        )
    }

    @Test
    fun `work mode restricts camera only`() {
        val environment = EvaluationEnvironment(
            rules = ModeCatalog.seedRules(),
            snapshot = ContextSnapshot(activeMode = ModeIds.WORK)
        )
        assertEquals(
            AvailabilityState.Restricted(RestrictionReason.FOCUS_MODE),
            RuleEngine.evaluate(environment, CapabilityRef.Logical(LogicalCapabilities.CAMERA))
        )
        assertEquals(
            AvailabilityState.Available,
            RuleEngine.evaluate(environment, CapabilityRef.Logical(LogicalCapabilities.CALL))
        )
    }

    @Test
    fun `restrictions disappear once another mode is active`() {
        val environment = EvaluationEnvironment(
            rules = ModeCatalog.seedRules(),
            snapshot = ContextSnapshot(activeMode = ModeIds.REST)
        )
        assertEquals(
            AvailabilityState.Available,
            RuleEngine.evaluate(environment, CapabilityRef.Logical(LogicalCapabilities.CAMERA))
        )
    }

    @Test
    fun `profiles cover exactly the stable mode ids`() {
        assertEquals(ModeIds.ALL, ModeCatalog.PROFILES.map { it.id })
    }
}

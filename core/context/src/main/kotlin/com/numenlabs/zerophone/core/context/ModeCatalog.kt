package com.numenlabs.zerophone.core.context

/**
 * Named mode profiles (WORK / REST / FOCUS) seeded into the rule store on the
 * first launch. Seed design is deliberately conservative:
 *
 *  - a BASELINE rule makes every logical quick-action capability available;
 *    without it logical capabilities would default to BLOCKED;
 *  - mode rules only ever RESTRICT a logical capability while their mode is
 *    active (ActiveMode outranks Always on the same target, so the mode rule
 *    wins deterministically while the mode is on and the baseline applies
 *    otherwise);
 *  - seeds never target packages (RuleTarget.Package/All), so the seeded
 *    ruleset can never widen or narrow the allowlist-based package defaults.
 */
object ModeCatalog {

    data class ModeProfile(
        val id: String,
        val rules: List<Rule> = emptyList()
    )

    /** Baseline availability of the logical capabilities in every mode. */
    val BASELINE_RULES: List<Rule> = LogicalCapabilities.ALL.map { capability ->
        Rule(
            id = "baseline.$capability",
            target = RuleTarget.Logical(capability),
            condition = RuleCondition.Always,
            decision = RuleDecision.Allow
        )
    }

    val WORK: ModeProfile = ModeProfile(
        id = ModeIds.WORK,
        rules = listOf(
            Rule(
                id = "mode.work.camera",
                target = RuleTarget.Logical(LogicalCapabilities.CAMERA),
                condition = RuleCondition.ActiveMode(ModeIds.WORK),
                decision = RuleDecision.Restrict(RestrictionReason.FOCUS_MODE)
            )
        )
    )

    val REST: ModeProfile = ModeProfile(
        id = ModeIds.REST,
        rules = emptyList()
    )

    val FOCUS: ModeProfile = ModeProfile(
        id = ModeIds.FOCUS,
        rules = listOf(
            Rule(
                id = "mode.focus.call",
                target = RuleTarget.Logical(LogicalCapabilities.CALL),
                condition = RuleCondition.ActiveMode(ModeIds.FOCUS),
                decision = RuleDecision.Restrict(RestrictionReason.FOCUS_MODE)
            ),
            Rule(
                id = "mode.focus.message",
                target = RuleTarget.Logical(LogicalCapabilities.MESSAGE),
                condition = RuleCondition.ActiveMode(ModeIds.FOCUS),
                decision = RuleDecision.Restrict(RestrictionReason.FOCUS_MODE)
            ),
            Rule(
                id = "mode.focus.camera",
                target = RuleTarget.Logical(LogicalCapabilities.CAMERA),
                condition = RuleCondition.ActiveMode(ModeIds.FOCUS),
                decision = RuleDecision.Restrict(RestrictionReason.FOCUS_MODE)
            )
        )
    )

    val PROFILES: List<ModeProfile> = listOf(WORK, REST, FOCUS)

    /** Everything seeded into the rule store on first launch (idempotent check by the caller). */
    fun seedRules(): List<Rule> = BASELINE_RULES + PROFILES.flatMap { it.rules }
}

package com.numenlabs.zerophone.core.context

/**
 * Immutable inputs shared by every capability evaluation: persisted rules,
 * grants, allowlist, protected packages, the time-budget ledger and the
 * current context snapshot. Defaults describe a "deny everything not
 * allowlisted" phone, so an empty environment reproduces the pre-engine
 * allowlist semantics exactly.
 */
data class EvaluationEnvironment(
    val rules: List<Rule> = emptyList(),
    val grants: List<ManualGrant> = emptyList(),
    val allowlist: Set<String> = emptySet(),
    val protectedPackages: Set<String> = emptySet(),
    val budgetLedger: TimeBudgetLedger = TimeBudgetLedger(),
    val snapshot: ContextSnapshot = ContextSnapshot()
)

/**
 * Deterministic contextual availability engine (pure Kotlin, no Android).
 *
 * Every capability resolves to exactly one [AvailabilityState]:
 *
 *  1. PROTECTED packages are invariantly [AvailabilityState.Available] —
 *     matches the SuspendPolicy guarantee that protected packages are never
 *     suspended; rules cannot override this.
 *  2. An active (non-expired) [ManualGrant] wins over everything else and
 *     yields [AvailabilityState.TemporarilyAvailable] with the remaining time.
 *  3. Otherwise the most applicable rule wins — deterministic ordering:
 *     specificity (target rank, then condition rank) desc, priority desc,
 *     then rule id asc. The winning decision maps onto the five states;
 *     a Restrict decision with an exhausted daily budget resolves to
 *     [AvailabilityState.Blocked].
 *  4. With no applicable rule the default applies: allowlisted packages are
 *     [AvailabilityState.Available], everything else (including non-allowlisted
 *     packages — the current launcher semantics — and unknown logical
 *     capabilities) is [AvailabilityState.Blocked].
 */
object RuleEngine {

    fun evaluate(environment: EvaluationEnvironment, capability: CapabilityRef): AvailabilityState {
        if (capability is CapabilityRef.Package &&
            capability.packageName in environment.protectedPackages
        ) {
            return AvailabilityState.Available
        }

        val now = environment.snapshot.nowMillis
        environment.grants
            .filter { it.matches(capability) && it.isActive(now) }
            .maxByOrNull { it.deadlineMillis }
            ?.let { return AvailabilityState.TemporarilyAvailable(it.remainingMillis(now)) }

        winningRule(environment, capability)?.let { rule ->
            return mapDecision(rule, capability, environment)
        }

        return when {
            capability is CapabilityRef.Package && capability.packageName in environment.allowlist ->
                AvailabilityState.Available
            else -> AvailabilityState.Blocked
        }
    }

    fun evaluateAll(
        environment: EvaluationEnvironment,
        capabilities: Iterable<CapabilityRef>
    ): Map<CapabilityRef, AvailabilityState> = capabilities.associateWith { evaluate(environment, it) }

    /**
     * Remaining daily budget for a restricted capability (budget minus the
     * ledger's usage for the snapshot day), or null when the winning rule
     * carries no budget or an active grant already unlocked the capability
     * (no budget hint is shown while a grant holds). Zero when exhausted.
     */
    fun restrictBudget(environment: EvaluationEnvironment, capability: CapabilityRef): Long? {
        val now = environment.snapshot.nowMillis
        if (environment.grants.any { it.matches(capability) && it.isActive(now) }) return null
        val rule = winningRule(environment, capability) ?: return null
        val budget = (rule.decision as? RuleDecision.Restrict)?.dailyBudgetMillis ?: return null
        val used = environment.budgetLedger.usedFor(capability.id, environment.snapshot.epochDay)
        return (budget - used).coerceAtLeast(0L)
    }

    private fun winningRule(environment: EvaluationEnvironment, capability: CapabilityRef): Rule? =
        environment.rules
            .filter { it.target.matches(capability) && it.condition.holds(environment.snapshot) }
            .minWithOrNull(
                compareByDescending<Rule> { it.specificity }
                    .thenByDescending { it.priority }
                    .thenBy { it.id }
            )

    private fun mapDecision(
        rule: Rule,
        capability: CapabilityRef,
        environment: EvaluationEnvironment
    ): AvailabilityState = when (val decision = rule.decision) {
        RuleDecision.Allow -> AvailabilityState.Available
        is RuleDecision.Restrict -> {
            val budget = decision.dailyBudgetMillis
            if (budget != null && environment.budgetLedger.isExhausted(
                    capability.id,
                    environment.snapshot.epochDay,
                    budget
                )
            ) {
                AvailabilityState.Blocked
            } else {
                AvailabilityState.Restricted(decision.reason)
            }
        }
        is RuleDecision.ContextualAllow -> AvailabilityState.Contextual(
            requiredSignals = emptySet(),
            condition = decision.description.ifBlank { rule.condition.describe() }
        )
        RuleDecision.Block -> AvailabilityState.Blocked
    }
}

private fun RuleCondition.describe(): String = when (this) {
    is RuleCondition.TimeWindow -> "time window $startMinuteOfDay-$endMinuteOfDay"
    is RuleCondition.CalendarBusy -> "calendar busy"
    is RuleCondition.ActiveMode -> "mode $mode"
    RuleCondition.Always -> "always"
}

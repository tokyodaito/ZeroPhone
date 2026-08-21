package com.numenlabs.zerophone.core.policy

import com.numenlabs.zerophone.core.context.ManualGrant
import com.numenlabs.zerophone.core.context.ModeIds
import com.numenlabs.zerophone.core.context.Rule
import com.numenlabs.zerophone.core.context.TimeBudgetLedger

/**
 * Structured persistence contract for all ZeroPhone policy state:
 *  - allowlist: package names allowed to run;
 *  - emergency deadline: epoch-ms end of the emergency-unlock window (0 = none);
 *  - last suspended set: the last successfully applied suspend set;
 *  - contextual-engine state: custom rules, active mode, manual grants and
 *    the daily time-budget ledger.
 *
 * Suspend API so implementations are free to use structured async storage
 * (Preferences DataStore in the Android app). Pure Kotlin — no Android types —
 * so tests (and later the :desktop dashboard) can provide their own implementation.
 */
interface PolicyRepository {

    suspend fun getAllowlist(): Set<String>

    suspend fun setAllowlist(packages: Set<String>)

    suspend fun getEmergencyDeadline(): Long

    suspend fun setEmergencyDeadline(deadlineMillis: Long)

    suspend fun getLastSuspended(): Set<String>

    suspend fun setLastSuspended(packages: Set<String>)

    suspend fun getRules(): List<Rule>

    suspend fun setRules(rules: List<Rule>)

    suspend fun getGrants(): List<ManualGrant>

    suspend fun setGrants(grants: List<ManualGrant>)

    suspend fun getActiveMode(): String

    suspend fun setActiveMode(mode: String)

    suspend fun getTimeBudgetLedger(): TimeBudgetLedger

    suspend fun setTimeBudgetLedger(ledger: TimeBudgetLedger)
}

/**
 * Pure Kotlin in-memory [PolicyRepository]: unit tests, previews and any future
 * non-Android consumers. Returns defensive copies so callers cannot corrupt state.
 */
class InMemoryPolicyRepository : PolicyRepository {

    private var allowlist: Set<String> = emptySet()
    private var emergencyDeadline: Long = 0L
    private var lastSuspended: Set<String> = emptySet()
    private var rules: List<Rule> = emptyList()
    private var grants: List<ManualGrant> = emptyList()
    private var activeMode: String = ModeIds.WORK
    private var timeBudgetLedger: TimeBudgetLedger = TimeBudgetLedger()

    override suspend fun getAllowlist(): Set<String> = allowlist.toSet()

    override suspend fun setAllowlist(packages: Set<String>) {
        allowlist = packages.toSet()
    }

    override suspend fun getEmergencyDeadline(): Long = emergencyDeadline

    override suspend fun setEmergencyDeadline(deadlineMillis: Long) {
        emergencyDeadline = deadlineMillis
    }

    override suspend fun getLastSuspended(): Set<String> = lastSuspended.toSet()

    override suspend fun setLastSuspended(packages: Set<String>) {
        lastSuspended = packages.toSet()
    }

    override suspend fun getRules(): List<Rule> = rules.toList()

    override suspend fun setRules(rules: List<Rule>) {
        this.rules = rules.toList()
    }

    override suspend fun getGrants(): List<ManualGrant> = grants.toList()

    override suspend fun setGrants(grants: List<ManualGrant>) {
        this.grants = grants.toList()
    }

    override suspend fun getActiveMode(): String = activeMode

    override suspend fun setActiveMode(mode: String) {
        activeMode = mode
    }

    override suspend fun getTimeBudgetLedger(): TimeBudgetLedger = timeBudgetLedger

    override suspend fun setTimeBudgetLedger(ledger: TimeBudgetLedger) {
        timeBudgetLedger = ledger
    }
}

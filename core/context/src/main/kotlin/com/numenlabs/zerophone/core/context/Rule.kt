package com.numenlabs.zerophone.core.context

import kotlinx.serialization.Serializable

/**
 * What a rule applies to. [rank] drives deterministic conflict resolution:
 * a more specific target outranks a broader one regardless of priority.
 */
@Serializable
sealed interface RuleTarget {

    val rank: Int

    fun matches(capability: CapabilityRef): Boolean

    /** Matches every capability (least specific). */
    @Serializable
    data object All : RuleTarget {
        override val rank: Int get() = 1
        override fun matches(capability: CapabilityRef): Boolean = true
    }

    /** Matches one concrete package. */
    @Serializable
    data class Package(val packageName: String) : RuleTarget {
        override val rank: Int get() = 3
        override fun matches(capability: CapabilityRef): Boolean =
            capability is CapabilityRef.Package && capability.packageName == packageName
    }

    /** Matches a logical capability (e.g. "payment", "camera"). */
    @Serializable
    data class Logical(val name: String) : RuleTarget {
        override val rank: Int get() = 2
        override fun matches(capability: CapabilityRef): Boolean =
            capability is CapabilityRef.Logical && capability.name == name
    }
}

/**
 * When a rule applies. Conditions are evaluated against the current
 * [ContextSnapshot]; [rank] drives deterministic conflict resolution
 * (a TimeWindow outranks a mode/calendar condition, which outrank Always).
 */
@Serializable
sealed interface RuleCondition {

    val rank: Int

    fun holds(snapshot: ContextSnapshot): Boolean

    /** Unconditional rule. */
    @Serializable
    data object Always : RuleCondition {
        override val rank: Int get() = 0
        override fun holds(snapshot: ContextSnapshot): Boolean = true
    }

    /**
     * Applies inside a daily time window. Windows may wrap midnight
     * (startMinuteOfDay > endMinuteOfDay); then the previous day whose
     * evening the window starts on also matches. start == end is an empty window.
     */
    @Serializable
    data class TimeWindow(
        val startMinuteOfDay: Int,
        val endMinuteOfDay: Int,
        val days: Set<WeekDay> = WeekDay.ALL
    ) : RuleCondition {
        override val rank: Int get() = 3

        override fun holds(snapshot: ContextSnapshot): Boolean {
            val wraps = startMinuteOfDay > endMinuteOfDay
            val minute = snapshot.minuteOfDay
            val inWindow = if (wraps) {
                minute >= startMinuteOfDay || minute < endMinuteOfDay
            } else {
                minute >= startMinuteOfDay && minute < endMinuteOfDay
            }
            if (!inWindow) return false
            return if (wraps) {
                snapshot.dayOfWeek in days || snapshot.dayOfWeek.previous() in days
            } else {
                snapshot.dayOfWeek in days
            }
        }
    }

    /** Applies while a calendar event is happening right now. */
    @Serializable
    data class CalendarBusy(val required: Boolean = true) : RuleCondition {
        override val rank: Int get() = 2
        override fun holds(snapshot: ContextSnapshot): Boolean = snapshot.calendarBusy == required
    }

    /** Applies while the given mode is active. */
    @Serializable
    data class ActiveMode(val mode: String) : RuleCondition {
        override val rank: Int get() = 2
        override fun holds(snapshot: ContextSnapshot): Boolean = snapshot.activeMode == mode
    }
}

/**
 * What the winning rule decides for a capability, mapped 1:1 onto
 * [AvailabilityState] by the engine. TEMPORARY availability never comes from
 * rules — only from persisted [ManualGrant]s with an expiry.
 */
@Serializable
sealed interface RuleDecision {

    @Serializable
    data object Allow : RuleDecision

    /**
     * Available with restrictions. An optional [dailyBudgetMillis] turns the
     * restriction into a daily time budget: once the ledger for the current
     * UTC day records [dailyBudgetMillis] of usage, the capability resolves
     * to BLOCKED until the next day.
     */
    @Serializable
    data class Restrict(
        val reason: RestrictionReason,
        val dailyBudgetMillis: Long? = null
    ) : RuleDecision

    /** Available only while the rule's condition keeps holding. */
    @Serializable
    data class ContextualAllow(val description: String = "") : RuleDecision

    @Serializable
    data object Block : RuleDecision
}

/**
 * One availability rule. Conflict resolution is deterministic:
 *  1. higher [specificity] wins (target rank, then condition rank);
 *  2. then higher [priority] wins;
 *  3. then the lexicographically smaller [id] wins (stable tie-break).
 */
@Serializable
data class Rule(
    val id: String,
    val target: RuleTarget,
    val condition: RuleCondition,
    val decision: RuleDecision,
    val priority: Int = 0
) {
    val specificity: Int get() = target.rank * 10 + condition.rank
}

/**
 * A temporary, persisted unlock of a single capability. Grants outrank rules
 * (a grant is a deliberate user action, like a per-capability emergency
 * window) and expire automatically: once [deadlineMillis] passes, the grant
 * stops applying and the engine falls back to rules/defaults.
 */
@Serializable
data class ManualGrant(
    val capabilityId: String,
    val deadlineMillis: Long
) {
    fun isActive(nowMillis: Long): Boolean = deadlineMillis > nowMillis

    fun remainingMillis(nowMillis: Long): Long = (deadlineMillis - nowMillis).coerceAtLeast(0L)

    fun matches(capability: CapabilityRef): Boolean = capability.id == capabilityId
}

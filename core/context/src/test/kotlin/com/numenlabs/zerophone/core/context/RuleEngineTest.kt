package com.numenlabs.zerophone.core.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin tests of the phase-2 contextual engine: defaults, conflict
 * resolution, grants, time windows and time budgets.
 */
class RuleEngineTest {

    private val engine = RuleEngine

    private fun snapshot(
        now: Long = 1_000_000L,
        minuteOfDay: Int = 12 * 60, // noon
        dayOfWeek: WeekDay = WeekDay.WEDNESDAY,
        mode: String? = ModeIds.WORK,
        calendarBusy: Boolean = false
    ) = ContextSnapshot(
        nowMillis = now,
        minuteOfDay = minuteOfDay,
        dayOfWeek = dayOfWeek,
        activeMode = mode,
        calendarBusy = calendarBusy
    )

    // ---- defaults -----------------------------------------------------------

    @Test
    fun `non-allowlisted package defaults to blocked`() {
        val state = engine.evaluate(
            EvaluationEnvironment(allowlist = setOf("allowed.app")),
            CapabilityRef.Package("other.app")
        )
        assertEquals(AvailabilityState.Blocked, state)
    }

    @Test
    fun `allowlisted package defaults to available`() {
        val state = engine.evaluate(
            EvaluationEnvironment(allowlist = setOf("allowed.app")),
            CapabilityRef.Package("allowed.app")
        )
        assertEquals(AvailabilityState.Available, state)
    }

    @Test
    fun `logical capability without rules defaults to blocked`() {
        assertEquals(
            AvailabilityState.Blocked,
            engine.evaluate(EvaluationEnvironment(), CapabilityRef.Logical("payment"))
        )
    }

    @Test
    fun `protected package is invariantly available even with a block rule`() {
        val environment = EvaluationEnvironment(
            rules = listOf(
                Rule("block-all", RuleTarget.All, RuleCondition.Always, RuleDecision.Block, priority = 100)
            ),
            allowlist = emptySet(),
            protectedPackages = setOf("com.android.dialer")
        )
        assertEquals(
            AvailabilityState.Available,
            engine.evaluate(environment, CapabilityRef.Package("com.android.dialer"))
        )
    }

    // ---- rules ---------------------------------------------------------------

    @Test
    fun `allow rule unlocks a non-allowlisted package`() {
        val environment = EvaluationEnvironment(
            rules = listOf(
                Rule("allow-game", RuleTarget.Package("game.app"), RuleCondition.Always, RuleDecision.Allow)
            ),
            allowlist = emptySet()
        )
        assertEquals(
            AvailabilityState.Available,
            engine.evaluate(environment, CapabilityRef.Package("game.app"))
        )
    }

    @Test
    fun `block rule restricts an allowlisted package`() {
        val environment = EvaluationEnvironment(
            rules = listOf(
                Rule("no-game", RuleTarget.Package("game.app"), RuleCondition.Always, RuleDecision.Block)
            ),
            allowlist = setOf("game.app")
        )
        assertEquals(
            AvailabilityState.Blocked,
            engine.evaluate(environment, CapabilityRef.Package("game.app"))
        )
    }

    @Test
    fun `more specific target beats higher priority broad rule`() {
        val environment = EvaluationEnvironment(
            rules = listOf(
                Rule("a-allow-all", RuleTarget.All, RuleCondition.Always, RuleDecision.Allow, priority = 100),
                Rule("b-block-game", RuleTarget.Package("game.app"), RuleCondition.Always, RuleDecision.Block, priority = 0)
            )
        )
        assertEquals(
            AvailabilityState.Blocked,
            engine.evaluate(environment, CapabilityRef.Package("game.app"))
        )
    }

    @Test
    fun `same specificity - higher priority wins`() {
        val environment = EvaluationEnvironment(
            rules = listOf(
                Rule("low", RuleTarget.All, RuleCondition.Always, RuleDecision.Allow, priority = 1),
                Rule("high", RuleTarget.All, RuleCondition.Always, RuleDecision.Block, priority = 2)
            )
        )
        assertEquals(
            AvailabilityState.Blocked,
            engine.evaluate(environment, CapabilityRef.Package("any.app"))
        )
    }

    @Test
    fun `equal specificity and priority - smaller id wins deterministically`() {
        val environment = EvaluationEnvironment(
            rules = listOf(
                Rule("zzz", RuleTarget.All, RuleCondition.Always, RuleDecision.Block),
                Rule("aaa", RuleTarget.All, RuleCondition.Always, RuleDecision.Allow)
            )
        )
        assertEquals(
            AvailabilityState.Available,
            engine.evaluate(environment, CapabilityRef.Package("any.app"))
        )
    }

    @Test
    fun `specific condition outranks broad condition on same target`() {
        // TimeWindow (condition rank 3) beats Always (rank 0) for the same target.
        val environment = EvaluationEnvironment(
            rules = listOf(
                Rule("always-allow", RuleTarget.Package("game.app"), RuleCondition.Always, RuleDecision.Allow, priority = 50),
                Rule(
                    "night-block",
                    RuleTarget.Package("game.app"),
                    RuleCondition.TimeWindow(startMinuteOfDay = 22 * 60, endMinuteOfDay = 6 * 60),
                    RuleDecision.Block
                )
            ),
            snapshot = snapshot(minuteOfDay = 23 * 60) // inside the night window
        )
        assertEquals(
            AvailabilityState.Blocked,
            engine.evaluate(environment, CapabilityRef.Package("game.app"))
        )
    }

    // ---- conditions ----------------------------------------------------------

    @Test
    fun `time window matches inside and not outside`() {
        val rule = Rule(
            "lunch",
            RuleTarget.Package("game.app"),
            RuleCondition.TimeWindow(startMinuteOfDay = 12 * 60, endMinuteOfDay = 13 * 60),
            RuleDecision.Allow
        )
        assertEquals(
            AvailabilityState.Available,
            engine.evaluate(
                EvaluationEnvironment(rules = listOf(rule), snapshot = snapshot(minuteOfDay = 12 * 60 + 30)),
                CapabilityRef.Package("game.app")
            )
        )
        assertEquals(
            AvailabilityState.Blocked,
            engine.evaluate(
                EvaluationEnvironment(rules = listOf(rule), snapshot = snapshot(minuteOfDay = 14 * 60)),
                CapabilityRef.Package("game.app")
            )
        )
    }

    @Test
    fun `overnight window wraps midnight and counts the previous evening`() {
        val rule = Rule(
            "night",
            RuleTarget.Package("game.app"),
            RuleCondition.TimeWindow(
                startMinuteOfDay = 22 * 60,
                endMinuteOfDay = 2 * 60,
                days = setOf(WeekDay.FRIDAY) // Friday 22:00 -> Saturday 02:00
            ),
            RuleDecision.Allow
        )
        assertEquals(
            AvailabilityState.Available,
            engine.evaluate(
                EvaluationEnvironment(
                    rules = listOf(rule),
                    snapshot = snapshot(minuteOfDay = 1 * 60, dayOfWeek = WeekDay.SATURDAY)
                ),
                CapabilityRef.Package("game.app")
            )
        )
        assertEquals(
            AvailabilityState.Available,
            engine.evaluate(
                EvaluationEnvironment(
                    rules = listOf(rule),
                    snapshot = snapshot(minuteOfDay = 23 * 60, dayOfWeek = WeekDay.FRIDAY)
                ),
                CapabilityRef.Package("game.app")
            )
        )
        // Saturday 01:00 is only reachable from a Friday-evening start on Friday schedule,
        // but a window starting Saturday (not in days) must not match Sunday early morning.
        assertEquals(
            AvailabilityState.Blocked,
            engine.evaluate(
                EvaluationEnvironment(
                    rules = listOf(rule),
                    snapshot = snapshot(minuteOfDay = 1 * 60, dayOfWeek = WeekDay.SUNDAY)
                ),
                CapabilityRef.Package("game.app")
            )
        )
    }

    @Test
    fun `time window respects day filter`() {
        val rule = Rule(
            "weekdays",
            RuleTarget.Package("game.app"),
            RuleCondition.TimeWindow(0, 24 * 60, days = WeekDay.WEEKDAYS),
            RuleDecision.Allow
        )
        assertEquals(
            AvailabilityState.Available,
            engine.evaluate(
                EvaluationEnvironment(rules = listOf(rule), snapshot = snapshot(dayOfWeek = WeekDay.MONDAY)),
                CapabilityRef.Package("game.app")
            )
        )
        assertEquals(
            AvailabilityState.Blocked,
            engine.evaluate(
                EvaluationEnvironment(rules = listOf(rule), snapshot = snapshot(dayOfWeek = WeekDay.SUNDAY)),
                CapabilityRef.Package("game.app")
            )
        )
    }

    @Test
    fun `calendar busy condition`() {
        val rule = Rule(
            "meeting",
            RuleTarget.Package("game.app"),
            RuleCondition.CalendarBusy(),
            RuleDecision.Block
        )
        assertEquals(
            AvailabilityState.Blocked,
            engine.evaluate(
                EvaluationEnvironment(rules = listOf(rule), snapshot = snapshot(calendarBusy = true)),
                CapabilityRef.Package("game.app")
            )
        )
        assertEquals(
            AvailabilityState.Blocked, // default (not allowlisted)
            engine.evaluate(
                EvaluationEnvironment(rules = listOf(rule), snapshot = snapshot(calendarBusy = false)),
                CapabilityRef.Package("game.app")
            )
        )
    }

    @Test
    fun `contextual allow yields contextual state with condition description`() {
        val rule = Rule(
            "evening",
            RuleTarget.Package("game.app"),
            RuleCondition.TimeWindow(17 * 60, 23 * 60),
            RuleDecision.ContextualAllow("вечернее окно")
        )
        val state = engine.evaluate(
            EvaluationEnvironment(rules = listOf(rule), snapshot = snapshot(minuteOfDay = 18 * 60)),
            CapabilityRef.Package("game.app")
        )
        assertTrue(state is AvailabilityState.Contextual)
        assertEquals("вечернее окно", (state as AvailabilityState.Contextual).condition)
    }

    @Test
    fun `active mode condition`() {
        val rule = Rule(
            "rest-mode",
            RuleTarget.Logical("entertainment"),
            RuleCondition.ActiveMode(ModeIds.REST),
            RuleDecision.Allow
        )
        assertEquals(
            AvailabilityState.Available,
            engine.evaluate(
                EvaluationEnvironment(rules = listOf(rule), snapshot = snapshot(mode = ModeIds.REST)),
                CapabilityRef.Logical("entertainment")
            )
        )
        assertEquals(
            AvailabilityState.Blocked,
            engine.evaluate(
                EvaluationEnvironment(rules = listOf(rule), snapshot = snapshot(mode = ModeIds.WORK)),
                CapabilityRef.Logical("entertainment")
            )
        )
    }

    // ---- grants ---------------------------------------------------------------

    @Test
    fun `active grant overrides block rule with temporary availability`() {
        val now = 1_000_000L
        val environment = EvaluationEnvironment(
            rules = listOf(
                Rule("block", RuleTarget.Package("game.app"), RuleCondition.Always, RuleDecision.Block)
            ),
            grants = listOf(ManualGrant("game.app", now + 5 * 60_000L)),
            snapshot = snapshot(now = now)
        )
        val state = engine.evaluate(environment, CapabilityRef.Package("game.app"))
        assertTrue(state is AvailabilityState.TemporarilyAvailable)
        assertEquals(5 * 60_000L, (state as AvailabilityState.TemporarilyAvailable).remainingMillis)
    }

    @Test
    fun `expired grant is ignored and falls back to rules`() {
        val now = 1_000_000L
        val state = engine.evaluate(
            EvaluationEnvironment(
                grants = listOf(ManualGrant("game.app", now - 1L)),
                snapshot = snapshot(now = now)
            ),
            CapabilityRef.Package("game.app")
        )
        assertEquals(AvailabilityState.Blocked, state)
    }

    @Test
    fun `latest of several grants wins`() {
        val now = 1_000_000L
        val state = engine.evaluate(
            EvaluationEnvironment(
                grants = listOf(
                    ManualGrant("game.app", now + 10_000L),
                    ManualGrant("game.app", now + 60_000L)
                ),
                snapshot = snapshot(now = now)
            ),
            CapabilityRef.Package("game.app")
        )
        assertEquals(60_000L, (state as AvailabilityState.TemporarilyAvailable).remainingMillis)
    }

    @Test
    fun `grant for another capability does not apply`() {
        val now = 1_000_000L
        val state = engine.evaluate(
            EvaluationEnvironment(
                grants = listOf(ManualGrant("other.app", now + 60_000L)),
                snapshot = snapshot(now = now)
            ),
            CapabilityRef.Package("game.app")
        )
        assertEquals(AvailabilityState.Blocked, state)
    }

    // ---- time budgets ----------------------------------------------------------

    @Test
    fun `restrict with budget yields restricted until exhausted`() {
        val now = 3 * 24 * 60 * 60 * 1000L + 100L // day 3
        val rule = Rule(
            "budget",
            RuleTarget.Package("game.app"),
            RuleCondition.Always,
            RuleDecision.Restrict(RestrictionReason.TIME_BUDGET, dailyBudgetMillis = 30 * 60_000L)
        )
        val fresh = EvaluationEnvironment(rules = listOf(rule), snapshot = snapshot(now = now))
        assertEquals(
            AvailabilityState.Restricted(RestrictionReason.TIME_BUDGET),
            engine.evaluate(fresh, CapabilityRef.Package("game.app"))
        )
        val exhausted = fresh.copy(
            budgetLedger = TimeBudgetLedger()
                .withUsage("game.app", TimeBudgetLedger.epochDayOf(now), 30 * 60_000L)
        )
        assertEquals(AvailabilityState.Blocked, engine.evaluate(exhausted, CapabilityRef.Package("game.app")))
    }

    @Test
    fun `budget usage resets on a new day`() {
        val day2 = 2 * 24 * 60 * 60 * 1000L
        val day3 = 3 * 24 * 60 * 60 * 1000L
        val rule = Rule(
            "budget",
            RuleTarget.Package("game.app"),
            RuleCondition.Always,
            RuleDecision.Restrict(RestrictionReason.TIME_BUDGET, dailyBudgetMillis = 10_000L)
        )
        val ledger = TimeBudgetLedger().withUsage("game.app", TimeBudgetLedger.epochDayOf(day2), 10_000L)
        val environment = EvaluationEnvironment(
            rules = listOf(rule),
            budgetLedger = ledger,
            snapshot = snapshot(now = day3)
        )
        assertEquals(
            AvailabilityState.Restricted(RestrictionReason.TIME_BUDGET),
            engine.evaluate(environment, CapabilityRef.Package("game.app"))
        )
    }

    @Test
    fun `budget day follows the snapshot local epoch day not UTC`() {
        // 22:00 UTC on day 3 is already 01:00 of day 4 at UTC+3: usage recorded
        // for the local day must exhaust the budget even though the UTC-derived
        // day would still be day 3 with a fresh ledger.
        val now = 3 * 24 * 60 * 60 * 1000L + 22 * 60 * 60 * 1000L
        val utcOffsetMillis = 3 * 60 * 60 * 1000L
        val localDay = TimeBudgetLedger.epochDayOf(now, utcOffsetMillis)
        val rule = Rule(
            "budget",
            RuleTarget.Package("game.app"),
            RuleCondition.Always,
            RuleDecision.Restrict(RestrictionReason.TIME_BUDGET, dailyBudgetMillis = 10_000L)
        )
        val environment = EvaluationEnvironment(
            rules = listOf(rule),
            budgetLedger = TimeBudgetLedger().withUsage("game.app", localDay, 10_000L),
            snapshot = snapshot(now = now).copy(epochDay = localDay)
        )
        assertEquals(AvailabilityState.Blocked, engine.evaluate(environment, CapabilityRef.Package("game.app")))
    }

    @Test
    fun `restrict without budget is never blocked by the ledger`() {
        val now = 1_000_000L
        val state = engine.evaluate(
            EvaluationEnvironment(
                rules = listOf(
                    Rule(
                        "supervisor",
                        RuleTarget.Package("game.app"),
                        RuleCondition.Always,
                        RuleDecision.Restrict(RestrictionReason.SUPERVISOR)
                    )
                ),
                budgetLedger = TimeBudgetLedger().withUsage("game.app", TimeBudgetLedger.epochDayOf(now), 999_999L),
                snapshot = snapshot(now = now)
            ),
            CapabilityRef.Package("game.app")
        )
        assertEquals(AvailabilityState.Restricted(RestrictionReason.SUPERVISOR), state)
    }

    // ---- bulk ------------------------------------------------------------------

    @Test
    fun `evaluateAll resolves every capability exactly once`() {
        val environment = EvaluationEnvironment(
            allowlist = setOf("allowed.app"),
            protectedPackages = setOf("com.android.dialer")
        )
        val states = engine.evaluateAll(
            environment,
            listOf(
                CapabilityRef.Package("allowed.app"),
                CapabilityRef.Package("other.app"),
                CapabilityRef.Package("com.android.dialer"),
                CapabilityRef.Logical("payment")
            )
        )
        assertEquals(4, states.size)
        assertEquals(AvailabilityState.Available, states[CapabilityRef.Package("allowed.app")])
        assertEquals(AvailabilityState.Blocked, states[CapabilityRef.Package("other.app")])
        assertEquals(AvailabilityState.Available, states[CapabilityRef.Package("com.android.dialer")])
        assertEquals(AvailabilityState.Blocked, states[CapabilityRef.Logical("payment")])
    }

    @Test
    fun `empty environment preserves legacy allowlist semantics`() {
        // With no rules/grants the engine reproduces the pre-engine behaviour:
        // allowlisted + protected available, everything else blocked.
        val environment = EvaluationEnvironment(
            allowlist = setOf("a", "b"),
            protectedPackages = setOf("com.android.settings")
        )
        val launchable = listOf("a", "b", "com.android.settings", "x", "y")
        val states = engine.evaluateAll(environment, launchable.map { CapabilityRef.Package(it) })
        assertEquals(setOf("a", "b", "com.android.settings"), states.filterValues { it == AvailabilityState.Available }.keys.map { it.id }.toSet())
        assertEquals(setOf("x", "y"), states.filterValues { it == AvailabilityState.Blocked }.keys.map { it.id }.toSet())
    }
}

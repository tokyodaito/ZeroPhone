package com.numenlabs.zerophone.core.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import com.numenlabs.zerophone.ZeroDeviceAdminReceiver
import com.numenlabs.zerophone.core.context.AvailabilityState
import com.numenlabs.zerophone.core.context.CapabilityRef
import com.numenlabs.zerophone.core.context.EvaluationEnvironment
import com.numenlabs.zerophone.core.context.ManualGrant
import com.numenlabs.zerophone.core.context.ModeCatalog
import com.numenlabs.zerophone.core.context.Rule
import com.numenlabs.zerophone.core.context.RuleEngine
import com.numenlabs.zerophone.core.context.RuleSchedule
import com.numenlabs.zerophone.core.context.SnapshotProvider
import com.numenlabs.zerophone.core.context.TimeBudgetLedger
import com.numenlabs.zerophone.core.model.EmergencyWindow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/**
 * Android glue for the suspend policy. Every DevicePolicyManager call is guarded by
 * [isDeviceOwner] — without Device Owner nothing is blocked (safe emulator behaviour).
 *
 * [reconcile] is the single idempotent entry point used by:
 * app start/resume, alarm fire, boot, allowlist change, emergency-window expiry.
 * Persistence goes through the [PolicyRepository] interface (Preferences DataStore).
 *
 * Reconcile has several concurrent entry points (activity refresh, boot /
 * re-lock / package-event receivers); every mutating call is
 * serialized through [mutex] so a package event racing the re-lock alarm can
 * never land an unsuspend after the lock pass finished.
 *
 * The contextual [RuleEngine] decides which candidate packages resolve to
 * BLOCKED; [SuspendPolicy] remains the hard guardrail that filters
 * self/allowlist/protected packages before anything reaches DevicePolicyManager.
 */
class PolicyApplier(
    context: Context,
    private val store: PolicyRepository,
    private val snapshotProvider: SnapshotProvider = SnapshotProvider.None
) {

    private val mutex = Mutex()

    data class LauncherApp(
        val packageName: String,
        val label: String,
        val icon: Drawable
    )

    sealed interface ReconcileResult {
        object NotDeviceOwner : ReconcileResult
        data class EmergencyActive(val remainingMillis: Long) : ReconcileResult
        data class Locked(val suspendedPackages: Set<String>) : ReconcileResult
    }

    private val appContext = context.applicationContext
    private val adminComponent = ComponentName(appContext, ZeroDeviceAdminReceiver::class.java)
    private val devicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val packageManager = appContext.packageManager

    val selfPackageName: String = appContext.packageName

    fun isDeviceOwner(): Boolean = try {
        devicePolicyManager.isDeviceOwnerApp(selfPackageName)
    } catch (_: Exception) {
        false
    }

    suspend fun getAllowlist(): Set<String> = store.getAllowlist()

    suspend fun emergencyDeadline(): Long = store.getEmergencyDeadline()

    /** Configured emergency-window duration (persisted, default 30 minutes). */
    suspend fun emergencyDurationMillis(): Long = store.getEmergencyDurationMillis()

    suspend fun setEmergencyDuration(durationMillis: Long) {
        store.setEmergencyDurationMillis(durationMillis)
    }

    fun getLauncherApps(): List<LauncherApp> =
        packageManager.queryIntentActivities(launcherQueryIntent(), 0)
            .asSequence()
            .mapNotNull { resolveInfo -> resolveInfo.activityInfo?.let { resolveInfo to it } }
            .filter { (_, activityInfo) -> activityInfo.enabled }
            .distinctBy { (_, activityInfo) -> activityInfo.packageName }
            .map { (resolveInfo, activityInfo) ->
                LauncherApp(
                    packageName = activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()

    fun launchPackage(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(launchIntent)
    }

    suspend fun setAllowed(packageName: String, allowed: Boolean) = mutex.withLock {
        val updated = store.getAllowlist().toMutableSet()
        val changed = if (allowed) updated.add(packageName) else updated.remove(packageName)
        if (changed) store.setAllowlist(updated)
        reconcileInternal()
    }

    /** Active mode id (defaults to [com.numenlabs.zerophone.core.context.ModeIds.WORK]). */
    suspend fun getActiveMode(): String = store.getActiveMode()

    /** Switches the active mode and immediately re-evaluates the policy. */
    suspend fun setActiveMode(mode: String) = mutex.withLock {
        store.setActiveMode(mode)
        reconcileInternal()
    }

    /**
     * Seeds the mode-catalog rules on first use. Idempotent: an existing
     * (possibly user-configured) ruleset is never overwritten.
     */
    suspend fun ensureDefaultRules() = mutex.withLock {
        if (store.getRules().isEmpty()) store.setRules(ModeCatalog.seedRules())
    }

    /** Persisted rules (seeded by [ensureDefaultRules], edited in settings). */
    suspend fun getRules(): List<Rule> = store.getRules()

    /** Replaces the ruleset and immediately re-evaluates the policy. */
    suspend fun updateRules(rules: List<Rule>) = mutex.withLock {
        store.setRules(rules)
        reconcileInternal()
    }

    /**
     * States and remaining budgets of the logical capabilities in one pass —
     * a single [EvaluationEnvironment] (and therefore a single context
     * snapshot) is shared by all evaluations.
     */
    data class LogicalSnapshot(
        val states: Map<String, AvailabilityState> = emptyMap(),
        val budgetRemainingMillis: Map<String, Long?> = emptyMap()
    )

    /** One-pass availability + budget snapshot for quick actions. */
    suspend fun logicalSnapshot(capabilityIds: List<String>): LogicalSnapshot {
        val environment = evaluationEnvironment(getAllowlist())
        val refs = capabilityIds.map { CapabilityRef.Logical(it) }
        val states = RuleEngine.evaluateAll(environment, refs)
        val budgets = capabilityIds.associateWith { id ->
            RuleEngine.restrictBudget(environment, CapabilityRef.Logical(id))
        }
        return LogicalSnapshot(
            states = states.mapKeys { (ref, _) -> (ref as CapabilityRef.Logical).name },
            budgetRemainingMillis = budgets
        )
    }

    /**
     * Remaining daily budget for a capability whose winning rule carries one
     * (null otherwise) — drives the "осталось N мин" hint on quick actions.
     */
    suspend fun budgetRemainingFor(capability: CapabilityRef): Long? =
        RuleEngine.restrictBudget(evaluationEnvironment(getAllowlist()), capability)

    /**
     * Sets the absolute foreground usage of a capability for the current
     * device-local day. Idempotent: callers re-measure the whole day each
     * pass, so process restarts, racing refreshes and midnight crossings
     * cannot double-count or lose usage.
     */
    suspend fun setCapabilityUsage(capabilityId: String, usedMillis: Long) {
        if (usedMillis < 0L) return
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val utcOffsetMillis =
            (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)).toLong()
        store.setTimeBudgetLedger(
            store.getTimeBudgetLedger().withAbsoluteUsage(
                capabilityId,
                TimeBudgetLedger.epochDayOf(now, utcOffsetMillis),
                usedMillis
            )
        )
    }

    /**
     * Temporary per-capability unlock ("временно доступна"): persists a
     * [ManualGrant] that outranks rules until its deadline, re-evaluates the
     * policy immediately and re-uses the re-lock alarm so the grant's expiry
     * re-applies the suspend set automatically. Works without Device Owner too
     * (the grant still resolves in [availabilityOf]) — it just has nothing to
     * enforce on the suspend side, same safe no-op as everywhere else.
     */
    suspend fun grantCapability(capabilityId: String, durationMillis: Long) = mutex.withLock {
        val now = System.currentTimeMillis()
        val deadline = now + durationMillis
        val grants = store.getGrants().filter { it.isActive(now) } + ManualGrant(capabilityId, deadline)
        store.setGrants(grants)
        scheduleNextDeadline(now, deadline)
        reconcileInternal()
    }

    /** Current engine state for an arbitrary capability (packages and logical). */
    suspend fun availabilityOf(capability: CapabilityRef): AvailabilityState =
        RuleEngine.evaluate(evaluationEnvironment(getAllowlist()), capability)

    /**
     * Idempotent policy application (serialized with every other mutating
     * call). Reads the persisted emergency deadline:
     *  - active window -> keep unlocked, (re-)schedule the re-lock alarm;
     *  - no window / expired window -> clear deadline, cancel alarm, suspend blockables.
     */
    suspend fun reconcile(): ReconcileResult = mutex.withLock { reconcileInternal() }

    suspend fun startEmergencyUnlock(durationMillis: Long? = null): Boolean = mutex.withLock {
        if (!isDeviceOwner()) return@withLock false
        val duration = durationMillis ?: store.getEmergencyDurationMillis()
        val now = System.currentTimeMillis()
        val deadline = now + duration
        store.setEmergencyDeadline(deadline)
        unsuspendBlockables()
        scheduleNextDeadline(now, deadline)
        true
    }

    /**
     * Ends an active emergency window early ("Заблокировать сейчас"): clears
     * the deadline and re-applies the suspend policy immediately. Idempotent —
     * with no active window it is just a plain reconcile.
     */
    suspend fun cancelEmergencyUnlock() = mutex.withLock {
        store.setEmergencyDeadline(EmergencyWindow.NONE_DEADLINE)
        reconcileInternal()
    }

    private suspend fun reconcileInternal(): ReconcileResult {
        if (!isDeviceOwner()) {
            // Admin revoked mid-window: the deadline is meaningless without
            // Device Owner — clear it so the UI does not show a stuck window.
            if (store.getEmergencyDeadline() > System.currentTimeMillis()) {
                store.setEmergencyDeadline(EmergencyWindow.NONE_DEADLINE)
            }
            // Nothing to enforce — do not keep waking the process for rule
            // boundaries that cannot change anything.
            ReLockScheduler.cancelEvaluation(appContext)
            return ReconcileResult.NotDeviceOwner
        }
        pruneExpiredGrants()
        val deadline = store.getEmergencyDeadline()
        val result = when (val window = EmergencyWindow.evaluate(deadline, System.currentTimeMillis())) {
            is EmergencyWindow.Active -> {
                ReLockScheduler.schedule(appContext, deadline)
                unsuspendBlockables()
                ReconcileResult.EmergencyActive(window.remainingMillis)
            }
            EmergencyWindow.None, EmergencyWindow.Expired -> lock()
        }
        scheduleNextRuleEvaluation()
        return result
    }

    /**
     * Keeps a reconcile alarm at the next time-window boundary so rules like
     * "block from 22:00" take effect without a launcher resume. No time
     * windows — no alarm.
     */
    private suspend fun scheduleNextRuleEvaluation() {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val zone = calendar.timeZone
        val utcOffsetMillis =
            (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)).toLong()
        val next = RuleSchedule.nextBoundaryMillis(store.getRules(), now, utcOffsetMillis) { millis ->
            zone.getOffset(millis).toLong()
        }
        if (next != null) {
            ReLockScheduler.scheduleEvaluation(appContext, next)
        } else {
            ReLockScheduler.cancelEvaluation(appContext)
        }
    }

    /**
     * Schedules the re-lock alarm at the earliest of the candidate deadline and
     * any active emergency/grant deadline, so no re-lock is delayed by another.
     * The alarm's reconcile is idempotent and re-schedules whatever is still
     * active when it fires.
     */
    private suspend fun scheduleNextDeadline(now: Long, candidateDeadline: Long) {
        val emergencyDeadline = store.getEmergencyDeadline().takeIf { it > now }
        val next = listOfNotNull(candidateDeadline, emergencyDeadline).min()
        if (next > now) ReLockScheduler.schedule(appContext, next)
    }

    /** Drops expired grants so the persisted list only keeps live state. */
    private suspend fun pruneExpiredGrants() {
        val now = System.currentTimeMillis()
        val grants = store.getGrants()
        val active = grants.filter { it.isActive(now) }
        if (active.size != grants.size) store.setGrants(active)
    }

    private suspend fun lock(): ReconcileResult.Locked {
        val deadline = store.getEmergencyDeadline()
        if (deadline != EmergencyWindow.NONE_DEADLINE) {
            store.setEmergencyDeadline(EmergencyWindow.NONE_DEADLINE)
        }
        ReLockScheduler.cancel(appContext)
        val toSuspend = computeSuspendSet()
        val lastSuspended = store.getLastSuspended()
        // Dirty-check: suspension survives reboots and resumes, so when the
        // computed set equals the last APPLIED one the device state is already
        // correct — skip the DPM IPC and the DataStore write. A partially
        // applied previous pass is recorded as such, so failed packages are
        // retried on the next reconcile.
        if (toSuspend != lastSuspended) {
            // Release packages we suspended earlier that left the suspend set
            // (e.g. just allowlisted) so allowlist changes unblock immediately.
            val toRelease = SuspendPolicy.computeReleaseSet(lastSuspended, toSuspend)
            setPackagesSuspendedSafely(toRelease, suspended = false)
            val applied = setPackagesSuspendedSafely(toSuspend, suspended = true)
            store.setLastSuspended(applied)
        }
        return ReconcileResult.Locked(toSuspend)
    }

    private suspend fun unsuspendBlockables() {
        // Unsuspension deliberately bypasses the engine: an emergency window
        // releases every package the guardrail could ever have suspended —
        // unconditionally, as a catch-all for any state drift (a crash
        // between the DPM write and the lastSuspended bookkeeping, external
        // interference, ...).
        val toUnsuspend = fullBlockableSet() + store.getLastSuspended()
        setPackagesSuspendedSafely(toUnsuspend, suspended = false)
        // Clear so the following lock() dirty-check does not mistake the
        // pre-window state for the current (unlocked) one.
        store.setLastSuspended(emptySet())
    }

    /** Every non-protected launchable package, ignoring allowlist and engine state. */
    private fun fullBlockableSet(): Set<String> =
        SuspendPolicy.computeSuspendSet(
            selfPackage = selfPackageName,
            launchablePackages = queryLaunchablePackageNames(),
            allowlist = emptySet(),
            protectedPackages = SuspendPolicy.DEFAULT_PROTECTED_PACKAGES + dynamicProtectedPackages(),
            protectedPrefixes = SuspendPolicy.DEFAULT_PROTECTED_PREFIXES
        )

    private suspend fun computeSuspendSet(allowlist: Set<String>? = null): Set<String> {
        val effectiveAllowlist = allowlist ?: getAllowlist()
        val protected = SuspendPolicy.DEFAULT_PROTECTED_PACKAGES + dynamicProtectedPackages()
        // Hard guardrail first: self, allowlist, protected packages and prefixes
        // can never be suspended, whatever the engine decides.
        val candidates = SuspendPolicy.computeSuspendSet(
            selfPackage = selfPackageName,
            launchablePackages = queryLaunchablePackageNames(),
            allowlist = effectiveAllowlist,
            protectedPackages = protected,
            protectedPrefixes = SuspendPolicy.DEFAULT_PROTECTED_PREFIXES
        )
        if (candidates.isEmpty()) return emptySet()
        val environment = evaluationEnvironment(effectiveAllowlist, protected)
        val states = RuleEngine.evaluateAll(environment, candidates.map { CapabilityRef.Package(it) })
        return EngineSuspendPolicy.computeSuspendSet(candidates, states)
    }

    /** Builds the engine environment from persisted policy state + current context. */
    private suspend fun evaluationEnvironment(
        allowlist: Set<String>,
        protectedPackages: Set<String> = SuspendPolicy.DEFAULT_PROTECTED_PACKAGES
    ): EvaluationEnvironment {
        val now = System.currentTimeMillis()
        return EvaluationEnvironment(
            rules = store.getRules(),
            grants = store.getGrants(),
            allowlist = allowlist,
            protectedPackages = protectedPackages,
            budgetLedger = store.getTimeBudgetLedger(),
            snapshot = snapshotProvider.snapshot(nowMillis = now, activeMode = store.getActiveMode())
        )
    }

    private fun queryLaunchablePackageNames(): Set<String> =
        packageManager.queryIntentActivities(launcherQueryIntent(), 0)
            .asSequence()
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

    private fun launcherQueryIntent(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    /** Extra runtime-protected packages: active IME and the current default home launcher. */
    private fun dynamicProtectedPackages(): Set<String> {
        val protected = mutableSetOf<String>()
        try {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
                ?.takeIf { it.isNotBlank() }
                ?.let { protected.add(it) }
        } catch (_: Exception) {
        }
        try {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
                ?.takeIf { it != selfPackageName }
                ?.let { protected.add(it) }
        } catch (_: Exception) {
        }
        return protected
    }

    /**
     * Applies suspension in one batch, retrying failed packages individually.
     * Packages whose suspension throws are skipped (treated as protected) —
     * never fatal. Returns the packages actually applied, so the caller's
     * bookkeeping only records reality: a partially applied set differs from
     * the target and is retried on the next reconcile.
     */
    private fun setPackagesSuspendedSafely(packages: Set<String>, suspended: Boolean): Set<String> {
        if (packages.isEmpty()) return emptySet()
        val failed: List<String> = try {
            val failedNames = devicePolicyManager.setPackagesSuspended(
                adminComponent, packages.toTypedArray(), suspended
            )
            if (failedNames == null) emptyList() else packages.filter { it in failedNames }
        } catch (_: Exception) {
            packages.toList()
        }
        val applied = (packages - failed.toSet()).toMutableSet()
        for (packageName in failed) {
            try {
                devicePolicyManager.setPackagesSuspended(
                    adminComponent, arrayOf(packageName), suspended
                )
                applied.add(packageName)
            } catch (_: Exception) {
                // Package refuses suspension — skip it (protected).
            }
        }
        return applied
    }
}

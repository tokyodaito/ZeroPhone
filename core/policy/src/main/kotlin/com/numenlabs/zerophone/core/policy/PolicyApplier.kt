package com.numenlabs.zerophone.core.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import com.numenlabs.zerophone.ZeroDeviceAdminReceiver
import com.numenlabs.zerophone.core.model.EmergencyWindow

/**
 * Android glue for the suspend policy. Every DevicePolicyManager call is guarded by
 * [isDeviceOwner] — without Device Owner nothing is blocked (safe emulator behaviour).
 *
 * [reconcile] is the single idempotent entry point used by:
 * app start/resume, alarm fire, boot, allowlist change, emergency-window expiry.
 */
class PolicyApplier(context: Context) {

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
    private val store = PolicyStore(appContext)

    val selfPackageName: String = appContext.packageName

    fun isDeviceOwner(): Boolean = try {
        devicePolicyManager.isDeviceOwnerApp(selfPackageName)
    } catch (_: Exception) {
        false
    }

    fun getAllowlist(): Set<String> = store.getAllowlist()

    fun emergencyDeadline(): Long = store.getEmergencyDeadline()

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

    fun setAllowed(packageName: String, allowed: Boolean) {
        store.setAllowed(packageName, allowed)
        reconcile()
    }

    /**
     * Idempotent policy application. Reads the persisted emergency deadline:
     *  - active window -> keep unlocked, (re-)schedule the re-lock alarm;
     *  - no window / expired window -> clear deadline, cancel alarm, suspend blockables.
     */
    fun reconcile(): ReconcileResult {
        if (!isDeviceOwner()) return ReconcileResult.NotDeviceOwner
        val deadline = store.getEmergencyDeadline()
        return when (val window = EmergencyWindow.evaluate(deadline, System.currentTimeMillis())) {
            is EmergencyWindow.Active -> {
                ReLockScheduler.schedule(appContext, deadline)
                unsuspendBlockables()
                ReconcileResult.EmergencyActive(window.remainingMillis)
            }
            EmergencyWindow.None, EmergencyWindow.Expired -> lock()
        }
    }

    fun startEmergencyUnlock(durationMillis: Long = EmergencyWindow.DEFAULT_DURATION_MS): Boolean {
        if (!isDeviceOwner()) return false
        val deadline = System.currentTimeMillis() + durationMillis
        store.setEmergencyDeadline(deadline)
        unsuspendBlockables()
        ReLockScheduler.schedule(appContext, deadline)
        return true
    }

    private fun lock(): ReconcileResult.Locked {
        store.setEmergencyDeadline(EmergencyWindow.NONE_DEADLINE)
        ReLockScheduler.cancel(appContext)
        val toSuspend = computeSuspendSet()
        setPackagesSuspendedSafely(toSuspend, suspended = true)
        store.setLastSuspended(toSuspend)
        return ReconcileResult.Locked(toSuspend)
    }

    private fun unsuspendBlockables() {
        val toUnsuspend = computeSuspendSet(allowlist = emptySet()) + store.getLastSuspended()
        setPackagesSuspendedSafely(toUnsuspend, suspended = false)
    }

    private fun computeSuspendSet(allowlist: Set<String> = getAllowlist()): Set<String> =
        SuspendPolicy.computeSuspendSet(
            selfPackage = selfPackageName,
            launchablePackages = queryLaunchablePackageNames(),
            allowlist = allowlist,
            protectedPackages = SuspendPolicy.DEFAULT_PROTECTED_PACKAGES + dynamicProtectedPackages(),
            protectedPrefixes = SuspendPolicy.DEFAULT_PROTECTED_PREFIXES
        )

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
     * Packages whose suspension throws are skipped (treated as protected) — never fatal.
     */
    private fun setPackagesSuspendedSafely(packages: Set<String>, suspended: Boolean) {
        if (packages.isEmpty()) return
        val failed: List<String> = try {
            val failedNames = devicePolicyManager.setPackagesSuspended(
                adminComponent, packages.toTypedArray(), suspended
            )
            if (failedNames == null) emptyList() else packages.filter { it in failedNames }
        } catch (_: Exception) {
            packages.toList()
        }
        for (packageName in failed) {
            try {
                devicePolicyManager.setPackagesSuspended(
                    adminComponent, arrayOf(packageName), suspended
                )
            } catch (_: Exception) {
                // Package refuses suspension — skip it (protected).
            }
        }
    }
}

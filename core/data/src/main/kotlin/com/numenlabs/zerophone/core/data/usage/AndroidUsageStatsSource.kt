package com.numenlabs.zerophone.core.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Foreground-usage source over [UsageStatsManager] for the daily time budgets
 * of logical capabilities. Requires the PACKAGE_USAGE_STATS appop (granted by
 * the Device Owner in [com.numenlabs.zerophone.core.policy.PolicyApplier]);
 * without it every call is a safe zero, so budgets simply do not accrue.
 */
class AndroidUsageStatsSource(context: Context) {

    private val appContext = context.applicationContext
    private val usageStatsManager = appContext.getSystemService(UsageStatsManager::class.java)
    private val appOpsManager = appContext.getSystemService(AppOpsManager::class.java)

    /** Whether the usage-stats appop is currently granted to this app. */
    fun isGranted(): Boolean {
        val manager = appOpsManager ?: return false
        val mode = try {
            // OPSTR_PACKAGE_USAGE_STATS was removed from recent SDKs — the op
            // id string itself is stable. unsafeCheckOpNoThrow is API 29+;
            // older devices use the deprecated checkOpNoThrow.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                manager.unsafeCheckOpNoThrow(
                    OP_PACKAGE_USAGE_STATS,
                    android.os.Process.myUid(),
                    appContext.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                manager.checkOpNoThrow(
                    OP_PACKAGE_USAGE_STATS,
                    android.os.Process.myUid(),
                    appContext.packageName
                )
            }
        } catch (_: Exception) {
            AppOpsManager.MODE_ERRORED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Milliseconds any of [packageNames] spent in the foreground between
     * [fromMillis] and [toMillis]; 0 when access is missing or the query
     * fails. Overlapping intervals of one app are counted once.
     */
    fun foregroundMillisBetween(
        packageNames: Set<String>,
        fromMillis: Long,
        toMillis: Long
    ): Long {
        if (usageStatsManager == null || packageNames.isEmpty() || toMillis <= fromMillis) return 0L
        if (!isGranted()) return 0L
        return try {
            val events = usageStatsManager.queryEvents(fromMillis, toMillis)
            val event = UsageEvents.Event()
            var tracked: String? = null
            var trackedSince = 0L
            var total = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isTrackedPackage = event.packageName in packageNames
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (tracked == null && isTrackedPackage) {
                            tracked = event.packageName
                            trackedSince = event.timeStamp
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (tracked == event.packageName) {
                            total += (event.timeStamp - trackedSince).coerceAtLeast(0L)
                            tracked = null
                        }
                    }
                    else -> Unit
                }
            }
            total
        } catch (_: Exception) {
            0L
        }
    }

    private companion object {
        const val OP_PACKAGE_USAGE_STATS = "android:package_usage_stats"
    }
}

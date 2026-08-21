package com.numenlabs.zerophone.core.policy

import android.content.Context

/**
 * Policy-layer facade over [AllowlistStore] (SharedPreferences "zerophone_prefs").
 * Delegation keeps a single source of truth: allowlist, emergency-unlock deadline
 * and the last successfully applied suspend set all persist through [AllowlistStore].
 */
class PolicyStore(context: Context) {

    private val store = AllowlistStore(context.applicationContext)

    fun getAllowlist(): Set<String> = store.getAllowlist()

    fun setAllowlist(packages: Set<String>) = store.setAllowlist(packages)

    fun setAllowed(packageName: String, allowed: Boolean) =
        store.setAllowed(packageName, allowed)

    fun getEmergencyDeadline(): Long = store.getEmergencyDeadline()

    fun setEmergencyDeadline(deadlineMillis: Long) =
        store.setEmergencyDeadline(deadlineMillis)

    fun getLastSuspended(): Set<String> = store.getLastSuspended()

    fun setLastSuspended(packages: Set<String>) = store.setLastSuspended(packages)
}

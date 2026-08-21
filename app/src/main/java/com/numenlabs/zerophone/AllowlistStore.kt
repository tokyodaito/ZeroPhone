package com.numenlabs.zerophone

import android.content.Context
import android.content.SharedPreferences

/**
 * Single source of truth for persisted ZeroPhone state, backed by
 * SharedPreferences("zerophone_prefs"):
 *  - "allowlist"          StringSet of package names allowed to run;
 *  - "emergency_deadline" Long epoch-ms end of the emergency-unlock window (0 = none);
 *  - "last_suspended"     StringSet of the last applied suspend set.
 *
 * Reads are synchronous at the call point; writes go through
 * [SharedPreferences.Editor.apply] so state survives process death and reboots.
 * Every allowlist mutation notifies registered listeners so the suspend policy
 * can be re-applied immediately (re-application must stay idempotent).
 */
class AllowlistStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val allowlistChangedListeners = mutableListOf<() -> Unit>()

    fun getAllowlist(): Set<String> =
        prefs.getStringSet(KEY_ALLOWLIST, emptySet())?.toSet() ?: emptySet()

    fun isAllowed(packageName: String): Boolean = packageName in getAllowlist()

    fun setAllowlist(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLOWLIST, packages.toSet()).apply()
        notifyAllowlistChanged()
    }

    fun setAllowed(packageName: String, allowed: Boolean) {
        val updated = getAllowlist().toMutableSet()
        val changed = if (allowed) updated.add(packageName) else updated.remove(packageName)
        if (changed) setAllowlist(updated)
    }

    fun getEmergencyDeadline(): Long =
        prefs.getLong(KEY_EMERGENCY_DEADLINE, NO_EMERGENCY_DEADLINE)

    fun setEmergencyDeadline(deadlineMillis: Long) {
        prefs.edit().putLong(KEY_EMERGENCY_DEADLINE, deadlineMillis).apply()
    }

    fun getLastSuspended(): Set<String> =
        prefs.getStringSet(KEY_LAST_SUSPENDED, emptySet())?.toSet() ?: emptySet()

    fun setLastSuspended(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_LAST_SUSPENDED, packages.toSet()).apply()
    }

    fun addOnAllowlistChangedListener(listener: () -> Unit) {
        allowlistChangedListeners.add(listener)
    }

    fun removeOnAllowlistChangedListener(listener: () -> Unit) {
        allowlistChangedListeners.remove(listener)
    }

    private fun notifyAllowlistChanged() {
        allowlistChangedListeners.toList().forEach { it() }
    }

    companion object {
        const val PREFS_NAME = "zerophone_prefs"
        const val KEY_ALLOWLIST = "allowlist"
        const val KEY_EMERGENCY_DEADLINE = "emergency_deadline"
        const val KEY_LAST_SUSPENDED = "last_suspended"
        const val NO_EMERGENCY_DEADLINE = 0L
    }
}

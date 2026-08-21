package com.numenlabs.zerophone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.numenlabs.zerophone.policy.PolicyApplier

/**
 * Fired by the AlarmManager when the emergency-unlock window ends.
 * Re-applies the suspend policy idempotently (reconcile is safe to run at any time:
 * it re-checks the persisted deadline, clears it and re-suspends the blockable set).
 */
class ReLockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RE_LOCK) return
        val pendingResult = goAsync()
        Thread {
            try {
                PolicyApplier(context.applicationContext).reconcile()
            } catch (_: Exception) {
                // Never crash the alarm pipeline; catch-up on next resume/boot covers it.
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_RE_LOCK = "com.numenlabs.zerophone.action.RE_LOCK"
    }
}

package com.numenlabs.zerophone.core.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Fired by the AlarmManager when the emergency-unlock window ends.
 * Re-applies the suspend policy idempotently (reconcile is safe to run at any time:
 * it re-checks the persisted deadline, clears it and re-suspends the blockable set).
 */
@AndroidEntryPoint
class ReLockAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var applier: PolicyApplier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RE_LOCK) return
        val pendingResult = goAsync()
        Thread {
            try {
                runBlocking { applier.reconcile() }
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

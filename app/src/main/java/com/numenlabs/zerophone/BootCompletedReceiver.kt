package com.numenlabs.zerophone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.numenlabs.zerophone.core.policy.PolicyApplier

/**
 * After a reboot:
 *  - if the emergency window is still active -> re-schedule the remaining re-lock alarm
 *    (package suspension state itself persists across reboots);
 *  - if the window expired while the device was off -> apply the suspend policy immediately;
 *  - if there was no window -> idempotently re-apply the policy.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        Thread {
            try {
                PolicyApplier(context.applicationContext).reconcile()
            } catch (_: Exception) {
                // Catch-up on app resume covers failures.
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

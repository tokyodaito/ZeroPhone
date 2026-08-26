package com.numenlabs.zerophone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.numenlabs.zerophone.core.policy.PolicyApplier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * ZeroPhone itself was updated (MY_PACKAGE_REPLACED is targeted at our
 * package, so unlike PACKAGE_ADDED/PACKAGE_REPLACED it is delivered to a
 * manifest receiver even with targetSdk 26+): re-apply the policy and
 * re-schedule a still-active re-lock deadline — same catch-up as boot.
 *
 * Installs/updates of OTHER apps are watched by the runtime registration in
 * [ZeroPhoneApplication]: those are implicit broadcasts a manifest receiver
 * never receives on modern Android.
 */
@AndroidEntryPoint
class PackageEventsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var applier: PolicyApplier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        Thread {
            try {
                runBlocking { applier.reconcile() }
            } catch (_: Exception) {
                // Catch-up on app resume covers failures.
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

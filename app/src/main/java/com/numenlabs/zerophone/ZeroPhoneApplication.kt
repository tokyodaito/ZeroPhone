package com.numenlabs.zerophone

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.numenlabs.zerophone.core.policy.PolicyApplier
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ZeroPhone application entry point: generates the Hilt singleton component
 * shared by the launcher activity, the boot receiver and the re-lock alarm.
 *
 * Also watches installs/updates of OTHER apps: with targetSdk 26+
 * PACKAGE_ADDED/PACKAGE_REPLACED are implicit broadcasts a manifest receiver
 * never gets, so they are caught via a runtime registration instead (the
 * launcher process is kept alive as HOME). Events are coalesced into one
 * debounced [PolicyApplier.reconcile] — a Play-store restore firing a hundred
 * broadcasts must not run a hundred full reconciles. Self-updates arrive as
 * the targeted MY_PACKAGE_REPLACED broadcast handled by [PackageEventsReceiver].
 */
@HiltAndroidApp
class ZeroPhoneApplication : Application() {

    @Inject
    lateinit var applier: PolicyApplier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reconcileJob: Job? = null

    private val packageEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED -> scheduleReconcile()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            // Package broadcasts carry a package: data URI — without the
            // scheme the intent filter never matches them.
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            packageEventsReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /** Debounced reconcile: bursts of package events collapse into a single run. */
    private fun scheduleReconcile() {
        reconcileJob?.cancel()
        reconcileJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            try {
                applier.reconcile()
            } catch (_: Exception) {
                // Catch-up on app resume covers failures.
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
    }
}

package com.numenlabs.zerophone.core.policy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules the automatic re-lock after the emergency-unlock window using
 * AlarmManager with RTC_WAKEUP — framework API only, no extra dependencies.
 *
 * Exact alarm (setExactAndAllowWhileIdle) is used when permitted; otherwise it
 * falls back to setAndAllowWhileIdle (inexact). Precision is guaranteed by the
 * persisted deadline + catch-up reconcile on boot / app resume, so a delayed
 * inexact alarm never leaves the device unlocked for long.
 */
object ReLockScheduler {

    private const val REQUEST_CODE = 1001

    fun schedule(context: Context, deadlineMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineMillis, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReLockAlarmReceiver::class.java)
                .setAction(ReLockAlarmReceiver.ACTION_RE_LOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

package com.numenlabs.zerophone.core.policy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules the automatic re-lock after the emergency-unlock window and the
 * rule re-evaluation at the next time-window boundary, using AlarmManager
 * with RTC_WAKEUP — framework API only, no extra dependencies. The two slots
 * are independent PendingIntents: cancelling a re-lock never cancels a
 * scheduled rule re-evaluation and vice versa.
 *
 * Exact alarm (setExactAndAllowWhileIdle) is used when permitted; otherwise it
 * falls back to setAndAllowWhileIdle (inexact). Precision is guaranteed by the
 * persisted deadline + catch-up reconcile on boot / app resume, so a delayed
 * inexact alarm never leaves the device unlocked for long.
 */
object ReLockScheduler {

    private const val RE_LOCK_REQUEST_CODE = 1001
    private const val EVALUATION_REQUEST_CODE = 1002

    fun schedule(context: Context, deadlineMillis: Long) {
        scheduleExact(context, deadlineMillis, pendingIntent(context))
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    /** One-shot reconcile at the next rule time-window boundary. */
    fun scheduleEvaluation(context: Context, atMillis: Long) {
        scheduleExact(context, atMillis, evaluationPendingIntent(context))
    }

    fun cancelEvaluation(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(evaluationPendingIntent(context))
    }

    private fun scheduleExact(context: Context, atMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            RE_LOCK_REQUEST_CODE,
            Intent(context, ReLockAlarmReceiver::class.java)
                .setAction(ReLockAlarmReceiver.ACTION_RE_LOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun evaluationPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            EVALUATION_REQUEST_CODE,
            Intent(context, ReLockAlarmReceiver::class.java)
                .setAction(ReLockAlarmReceiver.ACTION_REEVALUATE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

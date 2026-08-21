package com.numenlabs.zerophone.core.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.numenlabs.zerophone.core.model.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CalendarProvider-backed [CalendarSource]. Requires the runtime READ_CALENDAR
 * permission; without it every query safely returns "no data".
 */
class CalendarProviderSource(
    context: Context
) : CalendarSource {

    private val appContext = context.applicationContext

    override suspend fun nextEvent(
        nowMillis: Long,
        horizonMillis: Long
    ): CalendarEvent? = withContext(Dispatchers.IO) {
        queryOverlapping(nowMillis, nowMillis + horizonMillis)?.firstOrNull()
    }

    override fun isBusy(nowMillis: Long): Boolean =
        queryOverlapping(nowMillis - OVERLAP_SLACK_MILLIS, nowMillis)
            ?.any { it.isHappening(nowMillis) } == true

    /** Instances overlapping [fromMillis, toMillis] sorted by begin, or null when unavailable. */
    private fun queryOverlapping(fromMillis: Long, toMillis: Long): List<CalendarEvent>? {
        if (!hasReadPermission()) return null
        return try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(fromMillis.toString())
                .appendPath(toMillis.toString())
                .build()
            val events = mutableListOf<CalendarEvent>()
            appContext.contentResolver.query(
                uri,
                PROJECTION,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val locationIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                while (cursor.moveToNext()) {
                    events += CalendarEvent(
                        title = cursor.getString(titleIdx).orEmpty().ifBlank { UNTITLED },
                        beginMillis = cursor.getLong(beginIdx),
                        endMillis = cursor.getLong(endIdx),
                        allDay = cursor.getInt(allDayIdx) == 1,
                        location = cursor.getString(locationIdx)?.takeIf { it.isNotBlank() }
                    )
                }
            }
            events
        } catch (_: SecurityException) {
            // Permission revoked between the check and the query — degrade to "no data".
            null
        } catch (_: Exception) {
            // Calendar storage unavailable (e.g. no calendar app on the device).
            null
        }
    }

    private fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        val PROJECTION = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION
        )

        const val UNTITLED = "(без названия)"
        const val OVERLAP_SLACK_MILLIS = 24L * 60L * 60L * 1000L
    }
}

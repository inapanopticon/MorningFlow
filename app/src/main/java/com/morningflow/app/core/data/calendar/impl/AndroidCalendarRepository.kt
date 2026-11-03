package com.morningflow.app.core.data.calendar.impl

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.morningflow.app.core.data.calendar.api.CalendarRepository
import com.morningflow.app.core.data.calendar.api.CalendarPermissionSpec
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.calendar.CalendarEvent
import com.morningflow.app.core.permissions.AndroidPermissionChecker
import com.morningflow.app.core.permissions.PermissionChecker
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads visible Calendar Provider occurrences for the rest of the device's local day. */
class AndroidCalendarRepository internal constructor(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val permissionChecker: PermissionChecker = AndroidPermissionChecker(context),
    private val instancesClient: CalendarInstancesClient =
        ContentResolverCalendarInstancesClient(context.applicationContext.contentResolver),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CalendarRepository {
    override suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>> =
        withContext(ioDispatcher) {
            if (!permissionChecker.hasAll(CalendarPermissionSpec.requiredPermissions)) {
                return@withContext unavailable(CalendarFailureReason.PERMISSION_DENIED)
            }

            val now = Instant.now(clock)
            val nextMidnight = now
                .atZone(clock.zone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(clock.zone)
                .toInstant()

            try {
                val cursor = instancesClient.query(
                    projection = PROJECTION,
                    beginEpochMillis = now.toEpochMilli(),
                    endEpochMillis = nextMidnight.toEpochMilli(),
                ) ?: return@withContext unavailable(CalendarFailureReason.PROVIDER_UNAVAILABLE)

                cursor.use { readEvents(it, now) }
            } catch (_: SecurityException) {
                unavailable(CalendarFailureReason.PERMISSION_DENIED)
            } catch (_: Exception) {
                unavailable(CalendarFailureReason.QUERY_FAILED)
            }
        }

    private fun readEvents(cursor: Cursor, now: Instant): DataResult<List<CalendarEvent>> {
        val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
        val beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
        val endIndex = cursor.getColumnIndex(CalendarContract.Instances.END)
        val allDayIndex = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
        val statusIndex = cursor.getColumnIndex(CalendarContract.Instances.STATUS)
        val attendeeStatusIndex = cursor.getColumnIndex(
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )

        if (titleIndex < 0 || beginIndex < 0 || endIndex < 0 || allDayIndex < 0) {
            return unavailable(CalendarFailureReason.QUERY_FAILED)
        }

        val events = buildList {
            while (cursor.moveToNext()) {
                val row = CalendarEventMapper.map(
                    CalendarEventRow(
                        title = cursor.getStringOrNull(titleIndex),
                        startsAtEpochMillis = cursor.getLongOrNull(beginIndex),
                        endsAtEpochMillis = cursor.getLongOrNull(endIndex),
                        isAllDay = cursor.getInt(allDayIndex) != 0,
                        isCanceled = cursor.getIntOrNull(statusIndex) ==
                            CalendarContract.Instances.STATUS_CANCELED,
                        isDeclined = cursor.getIntOrNull(attendeeStatusIndex) ==
                            CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
                    ),
                ) ?: continue

                if (row.endsAt.isAfter(now)) add(row)
            }
        }.sortedWith(
            compareByDescending<CalendarEvent> { it.isAllDay }
                .thenBy { it.startsAt }
                .thenBy { it.title },
        )

        return DataResult.Success(events)
    }

    private fun unavailable(reason: CalendarFailureReason): DataResult.Unavailable =
        DataResult.Unavailable(reason.name)

    private companion object {
        /** Only fields needed for filtering and narration are requested from the provider. */
        val PROJECTION = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
    }
}

/** Injectable seam around the blocking Calendar Provider query. */
internal interface CalendarInstancesClient {
    fun query(
        projection: Array<String>,
        beginEpochMillis: Long,
        endEpochMillis: Long,
    ): Cursor?
}

private class ContentResolverCalendarInstancesClient(
    private val contentResolver: ContentResolver,
) : CalendarInstancesClient {
    override fun query(
        projection: Array<String>,
        beginEpochMillis: Long,
        endEpochMillis: Long,
    ): Cursor? = CalendarContract.Instances.query(
        contentResolver,
        projection,
        beginEpochMillis,
        endEpochMillis,
    )
}

private fun Cursor.getStringOrNull(index: Int): String? =
    if (index < 0 || isNull(index)) null else getString(index)

private fun Cursor.getLongOrNull(index: Int): Long? =
    if (index < 0 || isNull(index)) null else getLong(index)

private fun Cursor.getIntOrNull(index: Int): Int? =
    if (index < 0 || isNull(index)) null else getInt(index)

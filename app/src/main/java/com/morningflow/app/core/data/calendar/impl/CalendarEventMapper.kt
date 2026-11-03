package com.morningflow.app.core.data.calendar.impl

import com.morningflow.app.core.model.calendar.CalendarEvent
import java.time.Instant

/** Provider-independent row used to keep Calendar mapping tests free of Cursor details. */
internal data class CalendarEventRow(
    val title: String?,
    val startsAtEpochMillis: Long?,
    val endsAtEpochMillis: Long?,
    val isAllDay: Boolean,
    val isCanceled: Boolean,
    val isDeclined: Boolean,
)

internal object CalendarEventMapper {
    fun map(row: CalendarEventRow): CalendarEvent? {
        if (row.isCanceled || row.isDeclined) return null
        val title = row.title?.takeIf(String::isNotBlank) ?: return null
        val startsAt = row.startsAtEpochMillis?.let(Instant::ofEpochMilli) ?: return null
        val endsAt = row.endsAtEpochMillis?.let(Instant::ofEpochMilli) ?: return null
        if (endsAt.isBefore(startsAt)) return null

        return CalendarEvent(
            title = title,
            startsAt = startsAt,
            endsAt = endsAt,
            isAllDay = row.isAllDay,
        )
    }
}

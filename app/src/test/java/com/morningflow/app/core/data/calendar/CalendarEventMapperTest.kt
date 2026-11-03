package com.morningflow.app.core.data.calendar

import com.morningflow.app.core.data.calendar.impl.CalendarEventMapper
import com.morningflow.app.core.data.calendar.impl.CalendarEventRow
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarEventMapperTest {
    @Test
    fun mapsOnlyTheDomainFields() {
        val event = CalendarEventMapper.map(
            CalendarEventRow(
                title = "Morning meeting",
                startsAtEpochMillis = 1_000L,
                endsAtEpochMillis = 2_000L,
                isAllDay = false,
                isCanceled = false,
                isDeclined = false,
            ),
        )

        assertEquals("Morning meeting", event?.title)
        assertEquals(Instant.ofEpochMilli(1_000L), event?.startsAt)
        assertEquals(Instant.ofEpochMilli(2_000L), event?.endsAt)
        assertEquals(false, event?.isAllDay)
    }

    @Test
    fun dropsCanceledAndDeclinedEvents() {
        val row = CalendarEventRow(
            title = "Private appointment",
            startsAtEpochMillis = 1_000L,
            endsAtEpochMillis = 2_000L,
            isAllDay = false,
            isCanceled = true,
            isDeclined = false,
        )

        assertNull(CalendarEventMapper.map(row))
        assertNull(CalendarEventMapper.map(row.copy(isCanceled = false, isDeclined = true)))
    }

    @Test
    fun dropsRowsThatCannotBecomeValidDomainEvents() {
        val base = CalendarEventRow(
            title = "Valid",
            startsAtEpochMillis = 1_000L,
            endsAtEpochMillis = 2_000L,
            isAllDay = false,
            isCanceled = false,
            isDeclined = false,
        )

        assertNull(CalendarEventMapper.map(base.copy(title = " ")))
        assertNull(CalendarEventMapper.map(base.copy(startsAtEpochMillis = null)))
        assertNull(CalendarEventMapper.map(base.copy(endsAtEpochMillis = 500L)))
    }
}

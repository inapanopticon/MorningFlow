package com.morningflow.app.core.model.calendar

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalendarEventTest {
    @Test
    fun keepsAbsoluteTimesAndAllDayFlag() {
        val startsAt = Instant.parse("2026-09-29T00:00:00Z")
        val endsAt = Instant.parse("2026-09-30T00:00:00Z")

        val event = CalendarEvent(
            title = "Public holiday",
            startsAt = startsAt,
            endsAt = endsAt,
            isAllDay = true,
        )

        assertEquals(startsAt, event.startsAt)
        assertEquals(endsAt, event.endsAt)
        assertEquals(true, event.isAllDay)
    }

    @Test
    fun allowsAnEventEndingAtItsStartInstant() {
        val instant = Instant.parse("2026-09-29T09:00:00Z")

        val event = CalendarEvent(
            title = "Zero-duration marker",
            startsAt = instant,
            endsAt = instant,
            isAllDay = false,
        )

        assertEquals(instant, event.startsAt)
        assertEquals(instant, event.endsAt)
    }

    @Test
    fun rejectsBlankTitle() {
        assertThrows(IllegalArgumentException::class.java) {
            CalendarEvent(
                title = "   ",
                startsAt = Instant.parse("2026-09-29T09:00:00Z"),
                endsAt = Instant.parse("2026-09-29T10:00:00Z"),
                isAllDay = false,
            )
        }
    }

    @Test
    fun rejectsAnEndBeforeTheStart() {
        assertThrows(IllegalArgumentException::class.java) {
            CalendarEvent(
                title = "Invalid event",
                startsAt = Instant.parse("2026-09-29T10:00:00Z"),
                endsAt = Instant.parse("2026-09-29T09:00:00Z"),
                isAllDay = false,
            )
        }
    }
}

package com.morningflow.app.core.model.calendar

import java.time.Instant

/**
 * A calendar occurrence exposed to the MorningFlow domain.
 *
 * Among event metadata, the feature only needs the title for the leaving reminder. Descriptions,
 * attendees, locations, account information and other provider fields are intentionally not
 * fetched; the start/end instants and all-day flag are retained only because the use case needs
 * them. This keeps data access proportional to the use case and minimizes calendar privacy
 * exposure. Raw provider rows and timezone fields stay in the data layer. The domain keeps
 * absolute instants so callers can choose the display timezone, while [isAllDay] prevents an
 * all-day occurrence from being narrated as midnight.
 */
data class CalendarEvent(
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val isAllDay: Boolean,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(!endsAt.isBefore(startsAt)) { "endsAt must not be before startsAt" }
    }
}

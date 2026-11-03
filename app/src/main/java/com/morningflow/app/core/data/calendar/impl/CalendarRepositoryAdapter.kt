package com.morningflow.app.core.data.calendar.impl

import com.morningflow.app.core.data.CalendarRepository as LegacyCalendarRepository
import com.morningflow.app.core.data.calendar.api.CalendarRepository as FeatureCalendarRepository
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.calendar.CalendarEvent

/** Bridges the Calendar feature repository to the shared Session contract. */
class CalendarRepositoryAdapter(
    private val delegate: FeatureCalendarRepository,
) : LegacyCalendarRepository {
    override suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>> =
        delegate.getRemainingTodayEvents()
}

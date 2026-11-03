package com.morningflow.app.core.data.calendar.api

import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.calendar.CalendarEvent

/** Supplies the remaining calendar occurrences for the device's current local day. */
interface CalendarRepository {
    suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>>
}

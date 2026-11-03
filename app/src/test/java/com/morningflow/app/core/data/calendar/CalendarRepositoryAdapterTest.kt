package com.morningflow.app.core.data.calendar

import com.morningflow.app.core.data.CalendarRepository as LegacyCalendarRepository
import com.morningflow.app.core.data.calendar.api.CalendarRepository as FeatureCalendarRepository
import com.morningflow.app.core.data.calendar.impl.CalendarRepositoryAdapter
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.calendar.CalendarEvent
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarRepositoryAdapterTest {
    @Test
    fun preservesSuccessfulEventsAndEmptyResults() = runTest {
        val event = CalendarEvent(
            title = "Planning",
            startsAt = Instant.parse("2026-10-04T09:00:00Z"),
            endsAt = Instant.parse("2026-10-04T10:00:00Z"),
            isAllDay = false,
        )
        val results: List<DataResult<List<CalendarEvent>>> = listOf(
            DataResult.Success(listOf(event)),
            DataResult.Success(emptyList()),
        )
        var index = 0
        val delegate = object : FeatureCalendarRepository {
            override suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>> =
                results[index++]
        }

        val repository: LegacyCalendarRepository = CalendarRepositoryAdapter(delegate)

        assertEquals(results[0], repository.getRemainingTodayEvents())
        assertEquals(results[1], repository.getRemainingTodayEvents())
    }

    @Test
    fun preservesEveryUnavailableReason() = runTest {
        val reasons = listOf("PERMISSION_DENIED", "PROVIDER_UNAVAILABLE", "QUERY_FAILED")
        var index = 0
        val delegate = object : FeatureCalendarRepository {
            override suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>> =
                DataResult.Unavailable(reasons[index++])
        }

        val repository: LegacyCalendarRepository = CalendarRepositoryAdapter(delegate)

        reasons.forEach { reason ->
            assertEquals(DataResult.Unavailable(reason), repository.getRemainingTodayEvents())
        }
    }
}

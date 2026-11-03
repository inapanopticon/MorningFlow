package com.morningflow.app.core.data

import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.calendar.CalendarEvent
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarRepositoryContractTest {
    @Test
    fun fakeReturnsCompleteCalendarEventFieldsThroughUnifiedContract() = runTest {
        val event = CalendarEvent(
            title = "All-day planning",
            startsAt = Instant.parse("2026-09-29T00:00:00Z"),
            endsAt = Instant.parse("2026-09-30T00:00:00Z"),
            isAllDay = true,
        )

        val result = FakeCalendarRepository(DataResult.Success(listOf(event)))
            .getRemainingTodayEvents()

        assertEquals(DataResult.Success(listOf(event)), result)
    }

    @Test
    fun fakePreservesEmptyAndUnavailableResults() = runTest {
        val empty = FakeCalendarRepository(DataResult.Success(emptyList()))
            .getRemainingTodayEvents()
        val denied = FakeCalendarRepository(
            DataResult.Unavailable("PERMISSION_DENIED"),
        ).getRemainingTodayEvents()
        val providerUnavailable = FakeCalendarRepository(
            DataResult.Unavailable("PROVIDER_UNAVAILABLE"),
        ).getRemainingTodayEvents()
        val queryFailed = FakeCalendarRepository(
            DataResult.Unavailable("QUERY_FAILED"),
        ).getRemainingTodayEvents()

        assertEquals(DataResult.Success(emptyList<CalendarEvent>()), empty)
        assertEquals(DataResult.Unavailable("PERMISSION_DENIED"), denied)
        assertEquals(DataResult.Unavailable("PROVIDER_UNAVAILABLE"), providerUnavailable)
        assertEquals(DataResult.Unavailable("QUERY_FAILED"), queryFailed)
    }
}

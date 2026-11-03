package com.morningflow.app.core.domain.runtime

import com.morningflow.app.core.data.*
import com.morningflow.app.core.model.*
import com.morningflow.app.core.model.calendar.CalendarEvent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LeavingReminderLoaderTest {
    @Test fun calendarCanBeLoadedWithoutWaitingForLocationWeather() = runTest {
        val expected = DataResult.Success(listOf<CalendarEvent>())
        val loader = LeavingReminderLoader(object : WeatherRepository {
            override suspend fun getTodayWeather(): DataResult<WeatherInfo> = awaitCancellation()
        }, object : CalendarRepository {
            override suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>> = expected
        }, location = { awaitCancellation() }, locationWeather = { awaitCancellation() })

        assertEquals(expected, loader.loadCalendar())
    }

    @Test fun sourceFailureAndTimeoutAreIndependentFromSuccessfulEmptyCalendar() = runTest {
        val loader = LeavingReminderLoader(object : WeatherRepository {
            override suspend fun getTodayWeather(): DataResult<WeatherInfo> = error("offline")
        }, object : CalendarRepository {
            override suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>> = DataResult.Success(emptyList())
        }, location = { awaitCancellation() }, timeoutMillis = 100)
        val data = loader.load(AppSettings(thingsToBring = listOf("Keys")))
        assertEquals(DataResult.Unavailable("Location unavailable"), data.location)
        assertEquals(DataResult.Unavailable("Weather unavailable"), data.weather)
        assertEquals("Nothing scheduled next", data.calendarMessage)
        assertEquals(listOf("Keys"), data.thingsToBring)
    }
    @Test fun calendarFailureIsNotReportedAsAnEmptySchedule() = runTest {
        val loader = LeavingReminderLoader(object : WeatherRepository {
            override suspend fun getTodayWeather(): DataResult<WeatherInfo> = DataResult.Unavailable("offline")
        }, object : CalendarRepository {
            override suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>> = error("denied")
        }, location = { DataResult.Unavailable("denied") })
        assertEquals("Calendar Unavailable", loader.load(AppSettings()).calendarMessage)
    }
    @Test fun completeEventsAndFailureReasonsArePreserved() = runTest {
        val start = java.time.Instant.parse("2026-10-03T01:00:00Z")
        val events = listOf(CalendarEvent("Meeting", start, start.plusSeconds(3600), false),
            CalendarEvent("Holiday", start, start.plusSeconds(86400), true))
        val results = listOf<DataResult<List<CalendarEvent>>>(DataResult.Success(events),
            DataResult.Unavailable("PERMISSION_DENIED"),
            DataResult.Unavailable("PROVIDER_UNAVAILABLE"), DataResult.Unavailable("QUERY_FAILED"))
        for (result in results) {
            val loader = LeavingReminderLoader(object : WeatherRepository {
                override suspend fun getTodayWeather(): DataResult<WeatherInfo> = DataResult.Unavailable("offline")
            }, object : CalendarRepository {
                override suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>> = result
            }, location = { DataResult.Unavailable("offline") })
            val content = loader.load(AppSettings())
            assertEquals(result, content.calendar)
            assertEquals(if (result is DataResult.Unavailable) "Calendar Unavailable" else null, content.calendarMessage)
        }
    }

}

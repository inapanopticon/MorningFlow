package com.morningflow.app.feature.session

import com.morningflow.app.R
import com.morningflow.app.core.domain.runtime.LeavingContent
import com.morningflow.app.core.model.*
import com.morningflow.app.core.model.calendar.CalendarEvent
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class LeavingReminderPresenterTest {
    private val wetCold = WeatherInfo("Melbourne", "Rain", 8, 80, 7)
    private fun content(calendar: DataResult<List<CalendarEvent>> = DataResult.Success(emptyList()),
        weather: DataResult<WeatherInfo> = DataResult.Success(wetCold),
        location: DataResult<String> = DataResult.Success("Melbourne")) =
        LeavingContent(location, weather, calendar, listOf("Keys", "Laptop"))

    @Test fun productionMapperProvidesNonNullLeavingDataBeforeQueriesComplete() {
        val state = SessionUiState(status = SessionStatus.RUNNING, modules = listOf(
            RoutineModule(type = ModuleType.LEAVING_REMINDER, behaviours = listOf(
                Behaviour(key = BehaviourKey.LEAVING_REMINDER)))))
        val data = requireNotNull(SessionDisplayMapper.map(state, false))
        assertEquals(SessionStageContentType.LeavingReminder, data.stageContentType)
        assertEquals(R.string.session_leaving_loading, requireNotNull(data.leavingReminder).locationWeather.resourceId)
        assertEquals(R.string.session_leaving_loading, data.leavingReminder.scheduleItems.single().title.resourceId)
    }

    @Test fun realTitlesUseLocalTimeAndAllDayDoesNotShowMidnight() {
        val start = Instant.parse("2026-10-05T01:00:00Z")
        val events = listOf(CalendarEvent("All-day event", start, start.plusSeconds(86400), true),
            CalendarEvent("Project meeting", start, start.plusSeconds(3600), false))
        val data = LeavingReminderPresenter.map(content(DataResult.Success(events)), AppSettings(), ZoneId.of("Australia/Melbourne"))
        assertNull(data.scheduleItems.first().time)
        assertEquals(R.string.session_leaving_all_day_title, data.scheduleItems.first().title.resourceId)
        assertEquals(listOf("All-day event"), data.scheduleItems.first().title.formatArgs)
        assertEquals("12:00", data.scheduleItems[1].time)
        assertEquals(listOf("Project meeting"), data.scheduleItems[1].title.formatArgs)
        assertEquals(listOf(listOf("Keys"), listOf("Laptop")), data.thingsToBring.map { it.formatArgs })
    }

    @Test fun permissionProviderAndQueryFailuresDoNotLookLikeEmptySchedule() {
        for (reason in listOf("PERMISSION_DENIED", "PROVIDER_UNAVAILABLE", "QUERY_FAILED")) {
            val data = LeavingReminderPresenter.map(content(DataResult.Unavailable(reason)), AppSettings())
            assertEquals(R.string.session_calendar_unavailable, data.scheduleItems.single().title.resourceId)
        }
        val empty = LeavingReminderPresenter.map(content(), AppSettings())
        assertEquals(R.string.session_nothing_scheduled, empty.scheduleItems.single().title.resourceId)
    }

    @Test fun remindersRespectUserPreferencesAndInclusiveThresholds() {
        val on = AppSettings(reminders = ReminderPreferences(80, 8, true))
        assertEquals(3, LeavingReminderPresenter.map(content(), on).reminders.size)
        val off = on.copy(reminders = ReminderPreferences(null, null, false))
        assertTrue(LeavingReminderPresenter.map(content(), off).reminders.isEmpty())
        val mild = wetCold.copy(temperatureCelsius = 20, rainProbability = 5, uvIndex = 2)
        assertTrue(LeavingReminderPresenter.map(content(weather = DataResult.Success(mild)), on).reminders.isEmpty())
    }

    @Test fun locationAndWeatherFailuresAreIndependentAndKeepCalendarAndPackingList() {
        val unavailable = DataResult.Unavailable("denied")
        val noWeather = LeavingReminderPresenter.map(content(weather = unavailable), AppSettings())
        assertEquals(R.string.session_leaving_location_only, noWeather.locationWeather.resourceId)
        assertTrue(noWeather.reminders.isEmpty())
        val noLocation = LeavingReminderPresenter.map(content(location = unavailable), AppSettings())
        assertEquals(R.string.session_leaving_weather_only, noLocation.locationWeather.resourceId)
        val neither = LeavingReminderPresenter.map(content(location = unavailable, weather = unavailable), AppSettings())
        assertEquals(R.string.session_location_weather_unavailable, neither.locationWeather.resourceId)
        assertEquals(2, neither.thingsToBring.size)
        assertEquals(R.string.session_nothing_scheduled, neither.scheduleItems.single().title.resourceId)
    }
}

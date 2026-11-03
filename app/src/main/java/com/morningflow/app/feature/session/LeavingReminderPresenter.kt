package com.morningflow.app.feature.session

import com.morningflow.app.R
import com.morningflow.app.core.designsystem.icon.MorningFlowIcons
import com.morningflow.app.core.designsystem.time.ClockFormat
import com.morningflow.app.core.domain.runtime.LeavingContent
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.DataResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** UI projection only: the repositories own event selection and ordering. */
object LeavingReminderPresenter {
    fun loading() = LeavingReminderDisplayData(
        locationWeather = SessionDisplayText(R.string.session_leaving_loading),
        reminders = emptyList(), thingsToBring = emptyList(),
        scheduleItems = listOf(LeavingReminderScheduleItemDisplayData(
            title = SessionDisplayText(R.string.session_leaving_loading))),
    )

    fun map(content: LeavingContent, settings: AppSettings, zoneId: ZoneId = ZoneId.systemDefault()): LeavingReminderDisplayData {
        val weather = (content.weather as? DataResult.Success)?.value
        val location = (content.location as? DataResult.Success)?.value
        val locationWeather = when {
            location != null && weather != null -> SessionDisplayText(
                R.string.session_location_temperature_format, listOf(location, weather.temperatureCelsius))
            location != null -> SessionDisplayText(R.string.session_leaving_location_only, listOf(location))
            weather != null -> SessionDisplayText(R.string.session_leaving_weather_only, listOf(weather.temperatureCelsius))
            else -> SessionDisplayText(R.string.session_location_weather_unavailable)
        }
        val reminders = buildList {
            if (weather != null) {
                val preferences = settings.reminders
                if (preferences.umbrellaRainPercent?.let { weather.rainProbability >= it } == true)
                    add(LeavingReminderItemDisplayData(MorningFlowIcons.Umbrella,
                        SessionDisplayText(R.string.session_reminder_umbrella_title),
                        SessionDisplayText(R.string.session_reminder_umbrella_description)))
                if (preferences.jacketTemperatureCelsius?.let { weather.temperatureCelsius <= it } == true)
                    add(LeavingReminderItemDisplayData(MorningFlowIcons.ThermometerSnowflake,
                        SessionDisplayText(R.string.session_reminder_cool_title),
                        SessionDisplayText(R.string.session_reminder_cool_description)))
                // Matches the existing LeavingReminderNarration threshold.
                if (preferences.sunscreen && weather.uvIndex >= 6)
                    add(LeavingReminderItemDisplayData(MorningFlowIcons.Sun,
                        SessionDisplayText(R.string.session_reminder_uv_title),
                        SessionDisplayText(R.string.session_reminder_uv_description)))
            }
        }
        val schedule = when (val calendar = content.calendar) {
            is DataResult.Unavailable -> listOf(LeavingReminderScheduleItemDisplayData(
                title = SessionDisplayText(R.string.session_calendar_unavailable)))
            is DataResult.Success -> if (calendar.value.isEmpty()) listOf(LeavingReminderScheduleItemDisplayData(
                title = SessionDisplayText(R.string.session_nothing_scheduled)))
            else calendar.value.map { event -> LeavingReminderScheduleItemDisplayData(
                time = if (event.isAllDay) null else ClockFormat.format(event.startsAt.atZone(zoneId).toLocalTime()),
                title = if (event.isAllDay) SessionDisplayText(R.string.session_leaving_all_day_title, listOf(event.title))
                    else text(event.title),
            ) }
        }
        return LeavingReminderDisplayData(locationWeather, reminders, content.thingsToBring.map(::text), schedule)
    }

    private fun text(value: String) = SessionDisplayText(R.string.session_leaving_value, listOf(value))
}

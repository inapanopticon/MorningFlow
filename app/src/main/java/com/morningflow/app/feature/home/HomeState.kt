package com.morningflow.app.feature.home

import com.morningflow.app.core.designsystem.time.ClockFormat
import com.morningflow.app.core.domain.weatherlocation.LocationUnavailableReason
import com.morningflow.app.core.domain.weatherlocation.WeatherUnavailableReason
import com.morningflow.app.core.model.calendar.CalendarEvent
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


sealed interface HomeLocationState {
    data object Loading : HomeLocationState

    data class Available(val label: String) : HomeLocationState

    data object PermissionDenied : HomeLocationState

    data class Unavailable(val reason: LocationUnavailableReason) : HomeLocationState
}

sealed interface HomeWeatherState {
    data object Loading : HomeWeatherState

    data class Available(
        val temperatureCelsius: Int,
        val location: String,
        val condition: String,
        val isStale: Boolean = false,
    ) : HomeWeatherState

    data class Unavailable(val reason: WeatherUnavailableReason?) : HomeWeatherState
}

sealed interface HomeCalendarState {
    data object Loading : HomeCalendarState

    data class Available(val nextEvent: CalendarEvent) : HomeCalendarState

    data object Empty : HomeCalendarState

    data class Unavailable(val reason: String) : HomeCalendarState
}

data class HomeUiState(
    val displayData: HomeDisplayData,
    val timeState: HomeTimeState,
    val selectedTemplateId: String?,
    val selectedPlannedSeconds: Int?,
    val remainingSeconds: Long?,
    val readyBy: LocalTime,
    val location: HomeLocationState,
    val weather: HomeWeatherState,
    val calendar: HomeCalendarState,
) {
    val canStart: Boolean
        get() = timeState != HomeTimeState.INSUFFICIENT_TIME && selectedTemplateId != null
}

internal fun CalendarEvent.toHomeNextEventLabel(): String {
    if (isAllDay) return title
    val localTime = startsAt.atZone(ZoneId.systemDefault()).toLocalTime()
    return "$title ${ClockFormat.format(localTime)}"
}

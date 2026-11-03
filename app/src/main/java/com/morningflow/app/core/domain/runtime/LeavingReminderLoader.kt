package com.morningflow.app.core.domain.runtime

import com.morningflow.app.core.data.CalendarRepository
import com.morningflow.app.core.data.WeatherRepository
import com.morningflow.app.core.model.*
import com.morningflow.app.core.model.calendar.CalendarEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

data class LeavingContent(
    val location: DataResult<String>,
    val weather: DataResult<WeatherInfo>,
    val calendar: DataResult<List<CalendarEvent>>,
    val thingsToBring: List<String>,
) {
    val calendarMessage: String? get() = when (calendar) {
        is DataResult.Unavailable -> "Calendar Unavailable"
        is DataResult.Success -> if (calendar.value.isEmpty()) "Nothing scheduled next" else null
    }
}

/** Data loading is independent from FINISH; callers discard results for a departed behaviour token. */
class LeavingReminderLoader(
    private val weather: WeatherRepository,
    private val calendar: CalendarRepository,
    private val location: suspend () -> DataResult<String>,
    private val timeoutMillis: Long = 5_000,
    private val locationWeather: (suspend () -> Pair<DataResult<String>, DataResult<WeatherInfo>>)? = null,
) {
    init { require(timeoutMillis > 0) }
    suspend fun load(settings: AppSettings): LeavingContent = supervisorScope {
        val locationWeatherTask = async { loadLocationWeather() }
        val calendarTask = async { loadCalendar() }
        val pair = locationWeatherTask.await()
        LeavingContent(pair.first, pair.second, calendarTask.await(), settings.thingsToBring.toList())
    }

    suspend fun loadCalendar(): DataResult<List<CalendarEvent>> =
        fetch("Calendar") { calendar.getRemainingTodayEvents() }

    suspend fun loadLocationWeather(): Pair<DataResult<String>, DataResult<WeatherInfo>> = supervisorScope {
        val pairTask = locationWeather?.let { load -> async { load() } }
        val locationTask = if (pairTask == null) async { fetch("Location", location) } else null
        val weatherTask = if (pairTask == null) async { fetch("Weather") { weather.getTodayWeather() } } else null
        pairTask?.await() ?: (locationTask!!.await() to weatherTask!!.await())
    }

    private suspend fun <T> fetch(name: String, block: suspend () -> DataResult<T>): DataResult<T> = try {
        withTimeoutOrNull(timeoutMillis) { block() } ?: DataResult.Unavailable("$name unavailable")
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { DataResult.Unavailable("$name unavailable") }
}

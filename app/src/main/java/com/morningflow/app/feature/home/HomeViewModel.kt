package com.morningflow.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningflow.app.core.data.CalendarRepository
import com.morningflow.app.core.data.SettingsRepository
import com.morningflow.app.core.data.TemplateRepository
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowNavigationDestination
import com.morningflow.app.core.designsystem.illustration.MorningFlowIllustrations
import com.morningflow.app.core.domain.weatherlocation.WeatherLocationUiState
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.RoutineTemplate
import com.morningflow.app.core.model.WeatherInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

class HomeViewModel(
    private val templateRepository: TemplateRepository,
    private val settingsRepository: SettingsRepository,
    private val weatherLocationState: StateFlow<WeatherLocationUiState>,
    private val calendarRepository: CalendarRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val refreshWeatherLocation: suspend () -> Unit = {},
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val stateScope = scope ?: viewModelScope
    private val calendarState = MutableStateFlow<HomeCalendarState>(HomeCalendarState.Loading)
    private var expiredCalendarEventRefreshTime: Instant? = null
    private var calendarRefreshJob: Job? = null
    private val _uiState = MutableStateFlow(
        buildUiState(
            templates = templateRepository.templates.value,
            settings = settingsRepository.settings.value,
            weatherState = weatherLocationState.value,
            calendar = calendarState.value,
        ),
    )

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        stateScope.launch {
            combine(
                templateRepository.templates,
                settingsRepository.settings,
                weatherLocationState,
            ) { templates, settings, weather ->
                Triple(templates, settings, weather)
            }.collect { (templates, settings, weather) ->
                _uiState.value = buildUiState(
                    templates = templates,
                    settings = settings,
                    weatherState = weather,
                    calendar = calendarState.value,
                )
            }
        }
        refreshCalendar()
    }

    /** Refreshes the external data sources that Home can safely consume today. */
    fun refresh() {
        refreshCalendar()
        stateScope.launch { refreshWeatherLocation() }
    }

    /** Recomputes time-dependent Home state without requesting external data. */
    fun refreshTime() {
        rebuildFromCurrentInputs()
        val calendar = _uiState.value.calendar
        if (calendar is HomeCalendarState.Available &&
            !calendar.nextEvent.startsAt.isAfter(currentInstant()) &&
            expiredCalendarEventRefreshTime != calendar.nextEvent.startsAt
        ) {
            expiredCalendarEventRefreshTime = calendar.nextEvent.startsAt
            refreshCalendar()
        }
    }

    /** Refreshes Home's calendar projection without coupling Calendar data to lifecycle policy. */
    fun refreshCalendar() {
        if (calendarRefreshJob?.isActive == true) return
        calendarRefreshJob = stateScope.launch {
            val now = currentInstant()
            val result = try {
                calendarRepository.getRemainingTodayEvents()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DataResult.Unavailable("QUERY_FAILED")
            }
            calendarState.value = when (result) {
                is DataResult.Success -> result.value
                    .let { events ->
                        events
                            .filter { !it.isAllDay && it.startsAt.isAfter(now) }
                            .minByOrNull { it.startsAt }
                            ?: events.firstOrNull { it.isAllDay }
                    }
                    ?.let { HomeCalendarState.Available(it) }
                    ?: HomeCalendarState.Empty

                is DataResult.Unavailable -> HomeCalendarState.Unavailable(result.reason)
            }
            rebuildFromCurrentInputs()
        }
    }

    private fun rebuildFromCurrentInputs() {
        _uiState.value = buildUiState(
            templates = templateRepository.templates.value,
            settings = settingsRepository.settings.value,
            weatherState = weatherLocationState.value,
            calendar = calendarState.value,
        )
    }

    private fun buildUiState(
        templates: List<RoutineTemplate>,
        settings: AppSettings,
        weatherState: WeatherLocationUiState,
        calendar: HomeCalendarState,
    ): HomeUiState {
        val now = clock.instant().atZone(clock.zone)
        val currentTime = now.toLocalTime()
        val currentDay = now.dayOfWeek
        val selection = TemplateSelector.select(
            templates = templates,
            schedule = settings.schedule,
            currentDay = currentDay,
            currentTime = currentTime,
        )
        val selectedTemplate = selection.selectedTemplateId?.let { id ->
            templates.firstOrNull { it.id == id }
        }
        val location = weatherState.toLocationState()
        val weather = weatherState.toWeatherState()
        val displayData = HomeDisplayMapper.map(
            date = now.toLocalDate(),
            selection = selection,
            selectedTemplate = selectedTemplate,
            settings = settings,
            location = location,
            weather = weather,
            calendar = calendar,
        )

        return HomeUiState(
            displayData = displayData,
            timeState = selection.timeState,
            selectedTemplateId = selection.selectedTemplateId,
            selectedPlannedSeconds = selection.selectedPlannedSeconds,
            remainingSeconds = selection.remainingSeconds,
            readyBy = settings.schedule.readyBy,
            location = location,
            weather = weather,
            calendar = calendar,
        )
    }

    private fun currentTime(): LocalTime = clock.instant().atZone(clock.zone).toLocalTime()

    private fun currentInstant(): Instant = clock.instant()
}

private object HomeDisplayMapper {
    fun map(
        date: LocalDate,
        selection: TemplateSelection,
        selectedTemplate: RoutineTemplate?,
        settings: AppSettings,
        location: HomeLocationState,
        weather: HomeWeatherState,
        calendar: HomeCalendarState,
    ): HomeDisplayData {
        val remainingMinutes = selection.remainingSeconds?.div(60)?.toInt() ?: 0
        val modules = selectedTemplate?.modules
            ?.filter { it.enabled }
            ?.map { module ->
                HomeModuleDisplayData(
                    title = module.type.displayName,
                    durationMinutes = (module.plannedSeconds / 60.0).roundToInt(),
                )
            }
            .orEmpty()

        val weatherAvailable = weather as? HomeWeatherState.Available
        val locationLabel = when (location) {
            is HomeLocationState.Available -> location.label
            else -> "Location unavailable"
        }
        val weatherCondition = weatherAvailable?.condition ?: "Weather unavailable"
        val nextTodo = when (calendar) {
            is HomeCalendarState.Available -> calendar.nextEvent.toHomeNextEventLabel()
            HomeCalendarState.Empty -> "Nothing scheduled next."
            is HomeCalendarState.Unavailable -> "Calendar unavailable"
            HomeCalendarState.Loading -> "Checking calendar…"
        }

        return HomeDisplayData(
            date = date,
            temperatureCelsius = weatherAvailable?.temperatureCelsius ?: 0,
            location = weatherAvailable?.location ?: locationLabel,
            weatherCondition = weatherCondition,
            nextTodo = nextTodo,
            remainingMinutes = remainingMinutes,
            readyBy = settings.schedule.readyBy,
            earliestWakeUp = settings.schedule.earliestWakeUp,
            templateTitle = selectedTemplate?.name.orEmpty(),
            modules = modules,
            illustration = when (selection.timeState) {
                HomeTimeState.OFF_WINDOW -> MorningFlowIllustrations.HomeOutsideWakeupWindow
                HomeTimeState.INSUFFICIENT_TIME -> MorningFlowIllustrations.HomeInsufficientTime
                HomeTimeState.NORMAL -> null
            },
            selectedNavigation = MorningFlowNavigationDestination.Home,
            selectedTemplateId = selection.selectedTemplateId,
            plannedDurationSeconds = selection.selectedPlannedSeconds,
            timeState = selection.timeState,
        )
    }
}

private fun WeatherLocationUiState.toLocationState(): HomeLocationState = when (this) {
    WeatherLocationUiState.Idle,
    WeatherLocationUiState.Loading,
    is WeatherLocationUiState.Ready,
    is WeatherLocationUiState.ReadyWithStaleWeather,
    is WeatherLocationUiState.WeatherUnavailable,
    -> when (this) {
        is WeatherLocationUiState.Ready -> HomeLocationState.Available(location.displayLabel)
        is WeatherLocationUiState.ReadyWithStaleWeather -> HomeLocationState.Available(location.displayLabel)
        is WeatherLocationUiState.WeatherUnavailable -> HomeLocationState.Available(location.displayLabel)
        else -> HomeLocationState.Loading
    }

    WeatherLocationUiState.PermissionRequired -> HomeLocationState.PermissionDenied
    is WeatherLocationUiState.LocationUnavailable -> HomeLocationState.Unavailable(reason)
}

private fun WeatherLocationUiState.toWeatherState(): HomeWeatherState = when (this) {
    WeatherLocationUiState.Idle,
    WeatherLocationUiState.Loading,
    -> HomeWeatherState.Loading

    is WeatherLocationUiState.Ready -> weather.toHomeWeather(isStale = false)
    is WeatherLocationUiState.ReadyWithStaleWeather -> weather.toHomeWeather(isStale = true)
    is WeatherLocationUiState.WeatherUnavailable -> HomeWeatherState.Unavailable(reason)
    WeatherLocationUiState.PermissionRequired -> HomeWeatherState.Unavailable(null)
    is WeatherLocationUiState.LocationUnavailable -> HomeWeatherState.Unavailable(null)
}

private fun com.morningflow.app.core.model.weather.WeatherSnapshot.toHomeWeather(
    isStale: Boolean,
) = HomeWeatherState.Available(
    temperatureCelsius = temperatureCelsius.roundToInt(),
    location = locationLabel,
    condition = condition.displayName,
    isStale = isStale,
)

private val com.morningflow.app.core.model.weather.WeatherCondition.displayName: String
    get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

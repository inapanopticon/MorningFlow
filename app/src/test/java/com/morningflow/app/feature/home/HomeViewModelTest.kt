package com.morningflow.app.feature.home

import com.morningflow.app.core.data.FakeCalendarRepository
import com.morningflow.app.core.data.FakeSettingsRepository
import com.morningflow.app.core.data.FakeTemplateRepository
import com.morningflow.app.core.data.TemplateRepository
import com.morningflow.app.core.data.MutableClock
import com.morningflow.app.core.data.defaultTemplateSeed
import com.morningflow.app.core.data.testTemplateSeed
import com.morningflow.app.core.domain.weatherlocation.WeatherLocationUiState
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.GlobalMorningSchedule
import com.morningflow.app.core.model.Behaviour
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.RoutineModule
import com.morningflow.app.core.model.RoutineTemplate
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.location.LocationSource
import com.morningflow.app.core.model.weather.WeatherCondition
import com.morningflow.app.core.model.weather.WeatherSnapshot
import com.morningflow.app.core.model.calendar.CalendarEvent
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val clock = MutableClock(Instant.parse("2026-09-28T07:00:00Z"))
    private val weatherState = MutableStateFlow<WeatherLocationUiState>(readyWeather())

    @Test
    fun allDayCalendarEventUsesTitleOnlyForHomeDisplay() {
        val event = CalendarEvent(
            title = "Public holiday",
            startsAt = Instant.parse("2026-09-28T00:00:00Z"),
            endsAt = Instant.parse("2026-09-29T00:00:00Z"),
            isAllDay = true,
        )

        assertEquals("Public holiday", event.toHomeNextEventLabel())
    }

    @Test
    fun futureTimedEventTakesPriorityOverAllDayEvent() = runTest {
        val calendar = MutableCalendarRepository(
            DataResult.Success(
                listOf(
                    allDayEvent("Public holiday"),
                    calendarEvent("Dentist", LocalTime.of(8, 0)),
                ),
            ),
        )
        val viewModel = viewModel(scope = backgroundScope, calendar = calendar)

        val state = viewModel.uiState.first { it.calendar is HomeCalendarState.Available }

        assertEquals("Dentist", (state.calendar as HomeCalendarState.Available).nextEvent.title)
    }

    @Test
    fun nearestFutureTimedEventIsSelected() = runTest {
        val calendar = MutableCalendarRepository(
            DataResult.Success(
                listOf(
                    calendarEvent("Later", LocalTime.of(8, 30)),
                    calendarEvent("Sooner", LocalTime.of(7, 30)),
                ),
            ),
        )
        val viewModel = viewModel(scope = backgroundScope, calendar = calendar)

        val state = viewModel.uiState.first { it.calendar is HomeCalendarState.Available }

        assertEquals("Sooner", (state.calendar as HomeCalendarState.Available).nextEvent.title)
    }

    @Test
    fun allDayEventIsSelectedWhenNoFutureTimedEventRemains() = runTest {
        val calendar = MutableCalendarRepository(
            DataResult.Success(
                listOf(
                    calendarEvent("Started", LocalTime.of(6, 30)),
                    allDayEvent("Public holiday"),
                ),
            ),
        )
        val viewModel = viewModel(scope = backgroundScope, calendar = calendar)

        val state = viewModel.uiState.first { it.calendar is HomeCalendarState.Available }

        assertEquals("Public holiday", (state.calendar as HomeCalendarState.Available).nextEvent.title)
        assertEquals("Public holiday", state.displayData.nextTodo)
    }

    @Test
    fun onlyAllDayEventRemainsAvailableOnHome() = runTest {
        val calendar = MutableCalendarRepository(DataResult.Success(listOf(allDayEvent("Public holiday"))))
        val viewModel = viewModel(scope = backgroundScope, calendar = calendar)

        val state = viewModel.uiState.first { it.calendar is HomeCalendarState.Available }

        assertEquals("Public holiday", state.displayData.nextTodo)
    }

    @Test
    fun recommendationAndPreciseDurationReachHomeState() = runTest {
        val templates = FakeTemplateRepository()
        val selected = defaultTemplateSeed().first { it.isDefault }
        val viewModel = viewModel(scope = backgroundScope, templates = templates)

        val state = viewModel.uiState.first { it.calendar is HomeCalendarState.Available }

        assertEquals(HomeTimeState.NORMAL, state.timeState)
        assertEquals(selected.id, state.selectedTemplateId)
        assertEquals(selected.plannedSeconds, state.selectedPlannedSeconds)
        assertEquals(selected.plannedSeconds, state.displayData.plannedDurationSeconds)
        assertEquals(
            selected.modules.count { it.enabled },
            state.displayData.modules.size,
        )
        assertTrue(state.canStart)
    }

    @Test
    fun emptyTemplateListDisablesStartUntilTemplatesArrive() = runTest {
        val templates = DelayedTemplateRepository()
        val viewModel = viewModel(scope = backgroundScope, templates = templates)
        runCurrent()

        assertEquals(null, viewModel.uiState.value.selectedTemplateId)
        assertFalse(viewModel.uiState.value.canStart)

        templates.publish(defaultTemplateSeed())
        val ready = viewModel.uiState.first { it.selectedTemplateId != null }

        assertTrue(ready.canStart)
    }

    @Test
    fun templateRepositoryChangesRecomputeSelectedTemplateAndId() = runTest {
        val templates = FakeTemplateRepository()
        val viewModel = viewModel(scope = backgroundScope, templates = templates)
        viewModel.uiState.first { it.calendar is HomeCalendarState.Available }

        val later = testTemplateSeed().first { it.name == "Later Morning" }
        templates.save(later.copy(applicableStart = LocalTime.of(7, 0), priority = 5))

        val state = viewModel.uiState.first { it.selectedTemplateId == later.id }

        assertEquals(later.id, state.selectedTemplateId)
        assertEquals(later.plannedSeconds, state.selectedPlannedSeconds)
    }

    @Test
    fun explicitTimeRefreshCanSwitchNormalRecommendationWithoutRepositoryChanges() = runTest {
        val settings = FakeSettingsRepository()
        settings.update {
            it.copy(
                schedule = GlobalMorningSchedule(
                    earliestWakeUp = LocalTime.of(7, 0),
                    latestWakeUp = LocalTime.of(8, 0),
                    readyBy = LocalTime.of(9, 0),
                ),
            )
        }
        val viewModel = viewModel(
            scope = backgroundScope,
            settings = settings,
            templates = FakeTemplateRepository(testTemplateSeed()),
        )
        val initial = viewModel.uiState.first { it.calendar is HomeCalendarState.Available }

        assertEquals("recommended-30", initial.selectedTemplateId)

        clock.advance(Duration.ofMinutes(31))
        viewModel.refreshTime()

        val updated = viewModel.uiState.value
        assertEquals(HomeTimeState.NORMAL, updated.timeState)
        assertEquals("dev-same-time-high-priority", updated.selectedTemplateId)
        assertEquals(false, initial.selectedTemplateId == updated.selectedTemplateId)
    }

    @Test
    fun offWindowUsesActualDefaultTemplateAndNoRemainingCountdown() = runTest {
        clock.advance(java.time.Duration.ofMinutes(31))
        val defaultTemplate = defaultTemplateSeed().first { it.isDefault }
        val viewModel = viewModel(scope = backgroundScope)

        val state = viewModel.uiState.first { it.calendar is HomeCalendarState.Available }

        assertEquals(HomeTimeState.OFF_WINDOW, state.timeState)
        assertEquals(defaultTemplate.id, state.selectedTemplateId)
        assertEquals(defaultTemplate.name, state.displayData.templateTitle)
        assertEquals(null, state.remainingSeconds)
        assertTrue(state.canStart)
    }

    @Test
    fun insufficientTimeHidesAutomaticTemplateAndStart() = runTest {
        val settings = FakeSettingsRepository()
        settings.update {
            it.copy(
                schedule = GlobalMorningSchedule(
                    earliestWakeUp = LocalTime.of(7, 0),
                    latestWakeUp = LocalTime.of(8, 59),
                    readyBy = LocalTime.of(9, 0),
                ),
            )
        }
        clock.advance(java.time.Duration.ofMinutes(115))
        val viewModel = viewModel(scope = backgroundScope, settings = settings)

        val state = viewModel.uiState.first { it.calendar is HomeCalendarState.Available }

        assertEquals(HomeTimeState.INSUFFICIENT_TIME, state.timeState)
        assertEquals(null, state.selectedTemplateId)
        assertEquals(null, state.selectedPlannedSeconds)
        assertFalse(state.canStart)
        assertTrue(state.remainingSeconds!! > 0)
        assertTrue(state.displayData.modules.isEmpty())
    }

    @Test
    fun explicitTimeRefreshRecomputesWithoutRepositoryChanges() = runTest {
        val settings = FakeSettingsRepository()
        settings.update {
            it.copy(
                schedule = GlobalMorningSchedule(
                    earliestWakeUp = LocalTime.of(7, 0),
                    latestWakeUp = LocalTime.of(8, 59),
                    readyBy = LocalTime.of(9, 0),
                ),
            )
        }
        val viewModel = viewModel(scope = backgroundScope, settings = settings)
        val initial = viewModel.uiState.first { it.calendar is HomeCalendarState.Available }

        assertEquals(HomeTimeState.NORMAL, initial.timeState)
        assertEquals(defaultTemplateSeed().first { it.isDefault }.id, initial.selectedTemplateId)

        clock.advance(Duration.ofMinutes(115))
        viewModel.refreshTime()

        val updated = viewModel.uiState.value
        assertEquals(HomeTimeState.INSUFFICIENT_TIME, updated.timeState)
        assertEquals(null, updated.selectedTemplateId)
        assertEquals(5L * 60L, updated.remainingSeconds)
        assertEquals(null, updated.displayData.plannedDurationSeconds)
    }

    @Test
    fun totalDurationRoundsPreciseTemplateSecondsOnlyOnce() = runTest {
        val preciseTemplate = RoutineTemplate(
            id = "precise-rounding",
            name = "Precise Rounding",
            applicableDays = setOf(DayOfWeek.MONDAY),
            applicableStart = LocalTime.of(7, 0),
            modules = listOf(
                RoutineModule(
                    type = ModuleType.PHYSICAL_WAKE_UP,
                    stepTarget = 1,
                    behaviours = listOf(
                        Behaviour(
                            key = BehaviourKey.WALKING,
                            mode = BehaviourMode.ON,
                        ),
                    ),
                ),
                RoutineModule(
                    type = ModuleType.PHYSICAL_WAKE_UP,
                    stepTarget = 1,
                    behaviours = listOf(
                        Behaviour(
                            key = BehaviourKey.WALKING,
                            mode = BehaviourMode.ON,
                        ),
                    ),
                ),
            ),
        )
        val templates = FakeTemplateRepository()
        templates.save(preciseTemplate)
        val viewModel = viewModel(scope = backgroundScope, templates = templates)

        val state = viewModel.uiState.first { it.selectedTemplateId == preciseTemplate.id }

        assertEquals(64, state.selectedPlannedSeconds)
        assertEquals(listOf(1, 1), state.displayData.modules.map { it.durationMinutes })
        assertEquals(1, state.displayData.totalDurationMinutes)
    }

    @Test
    fun externalLocationAndWeatherStatesRemainIndependentFromTimeState() = runTest {
        val viewModel = viewModel(scope = backgroundScope)
        viewModel.uiState.first { it.calendar is HomeCalendarState.Available }

        weatherState.value = WeatherLocationUiState.PermissionRequired
        val denied = viewModel.uiState.first { it.location is HomeLocationState.PermissionDenied }

        assertEquals(HomeTimeState.NORMAL, denied.timeState)
        assertEquals(HomeWeatherState.Unavailable(null), denied.weather)
        assertTrue(denied.canStart)

        weatherState.value = WeatherLocationUiState.WeatherUnavailable(
            location = readyWeather().location,
            reason = com.morningflow.app.core.domain.weatherlocation.WeatherUnavailableReason.NETWORK,
        )
        val unavailable = viewModel.uiState.first {
            it.weather is HomeWeatherState.Unavailable &&
                it.location is HomeLocationState.Available
        }

        assertTrue(unavailable.location is HomeLocationState.Available)
        assertEquals(HomeTimeState.NORMAL, unavailable.timeState)
    }

    @Test
    fun calendarEmptyAndUnavailableDoNotChangeTemplateState() = runTest {
        val calendar = MutableCalendarRepository(DataResult.Success(emptyList()))
        val viewModel = viewModel(scope = backgroundScope, calendar = calendar)

        val empty = viewModel.uiState.first { it.calendar == HomeCalendarState.Empty }
        assertEquals(HomeTimeState.NORMAL, empty.timeState)
        assertTrue(empty.canStart)

        calendar.result = DataResult.Unavailable("QUERY_FAILED")
        viewModel.refresh()
        val unavailable = viewModel.uiState.first {
            it.calendar == HomeCalendarState.Unavailable("QUERY_FAILED")
        }

        assertEquals(HomeTimeState.NORMAL, unavailable.timeState)
        assertTrue(unavailable.canStart)
    }

    @Test
    fun calendarPermissionDeniedProviderUnavailableAndQueryFailedStayUnavailable() = runTest {
        val calendar = MutableCalendarRepository(DataResult.Unavailable("PERMISSION_DENIED"))
        val viewModel = viewModel(scope = backgroundScope, calendar = calendar)

        val denied = viewModel.uiState.first {
            it.calendar == HomeCalendarState.Unavailable("PERMISSION_DENIED")
        }
        assertTrue(denied.canStart)

        calendar.result = DataResult.Unavailable("PROVIDER_UNAVAILABLE")
        viewModel.refresh()
        viewModel.uiState.first {
            it.calendar == HomeCalendarState.Unavailable("PROVIDER_UNAVAILABLE")
        }

        calendar.result = DataResult.Unavailable("QUERY_FAILED")
        viewModel.refresh()
        val failed = viewModel.uiState.first {
            it.calendar == HomeCalendarState.Unavailable("QUERY_FAILED")
        }
        assertTrue(failed.canStart)
    }

    @Test
    fun calendarQueryExceptionBecomesRecoverableUnavailableState() = runTest {
        val calendar = ThrowingCalendarRepository()
        val viewModel = viewModel(scope = backgroundScope, calendar = calendar)

        val failed = viewModel.uiState.first {
            it.calendar == HomeCalendarState.Unavailable("QUERY_FAILED")
        }
        assertTrue(failed.canStart)

        calendar.throwOnQuery = false
        viewModel.refreshCalendar()
        val recovered = viewModel.uiState.first {
            it.calendar is HomeCalendarState.Available
        }
        assertEquals("Recovered", (recovered.calendar as HomeCalendarState.Available).nextEvent.title)
    }

    @Test
    fun calendarRefreshIsNotBlockedByWeatherRefresh() = runTest {
        val calendar = CountingCalendarRepository(
            listOf(
                DataResult.Success(emptyList()),
                DataResult.Success(listOf(calendarEvent("Next", LocalTime.of(8, 0)))),
            ),
        )
        var weatherRefreshStarted = false
        val viewModel = viewModel(
            scope = backgroundScope,
            calendar = calendar,
            refreshWeatherLocation = {
                weatherRefreshStarted = true
                awaitCancellation()
            },
        )
        viewModel.uiState.first { it.calendar == HomeCalendarState.Empty }

        viewModel.refresh()
        runCurrent()

        assertTrue(weatherRefreshStarted)
        assertEquals(2, calendar.calls)
    }

    @Test
    fun expiredCalendarEventRefreshesOnceAndConcurrentTimeRefreshesDoNotDuplicateRequest() = runTest {
        val calendar = CountingCalendarRepository(
            listOf(
                DataResult.Success(
                    listOf(
                        calendarEvent("First", LocalTime.of(7, 1)),
                        calendarEvent("Next", LocalTime.of(8, 0)),
                    ),
                ),
                DataResult.Success(listOf(calendarEvent("Next", LocalTime.of(8, 0)))),
            ),
        )
        val viewModel = viewModel(scope = backgroundScope, calendar = calendar)
        viewModel.uiState.first { it.calendar is HomeCalendarState.Available }
        advanceUntilIdle()
        assertEquals(1, calendar.calls)
        assertEquals(
            Instant.parse("2026-09-28T07:01:00Z"),
            (viewModel.uiState.value.calendar as HomeCalendarState.Available).nextEvent.startsAt,
        )

        clock.advance(Duration.ofMinutes(2))
        repeat(3) { viewModel.refreshTime() }

        val updated = viewModel.uiState.first {
            it.calendar == HomeCalendarState.Available(
                calendarEvent("Next", LocalTime.of(8, 0)),
            )
        }
        assertEquals(2, calendar.calls)
        assertEquals(
            HomeCalendarState.Available(
                calendarEvent("Next", LocalTime.of(8, 0)),
            ),
            updated.calendar,
        )

        repeat(3) { viewModel.refreshTime() }
        runCurrent()
        assertEquals(2, calendar.calls)
    }

    private fun viewModel(
        scope: CoroutineScope,
        templates: TemplateRepository = FakeTemplateRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        calendar: com.morningflow.app.core.data.CalendarRepository = FakeCalendarRepository(),
        refreshWeatherLocation: suspend () -> Unit = {},
    ) = HomeViewModel(
        templateRepository = templates,
        settingsRepository = settings,
        weatherLocationState = weatherState,
        calendarRepository = calendar,
        clock = clock,
        refreshWeatherLocation = refreshWeatherLocation,
        scope = scope,
    )

    private fun readyWeather() = WeatherLocationUiState.Ready(
        location = LocationSnapshot(
            latitude = -37.8136,
            longitude = 144.9631,
            accuracyMeters = null,
            capturedAtEpochMs = 0L,
            district = null,
            city = "Melbourne",
            adminArea = "Victoria",
            countryCode = "AU",
            displayLabel = "Melbourne",
            source = LocationSource.NETWORK,
        ),
        weather = WeatherSnapshot(
            locationKey = "melbourne",
            locationLabel = "Melbourne",
            temperatureCelsius = 24.4,
            apparentTemperatureCelsius = null,
            dailyMaxTemperatureCelsius = null,
            dailyMinTemperatureCelsius = null,
            condition = WeatherCondition.RAIN,
            rainProbability = 60,
            uvIndex = 4.0,
            fetchedAtEpochMs = 0L,
        ),
    )

    private fun calendarEvent(
        title: String,
        time: LocalTime,
        durationMinutes: Long = 30,
    ): CalendarEvent {
        val startsAt = Instant.parse("2026-09-28T${time}:00Z")
        return CalendarEvent(
            title = title,
            startsAt = startsAt,
            endsAt = startsAt.plusSeconds(durationMinutes * 60),
            isAllDay = false,
        )
    }

    private fun allDayEvent(title: String): CalendarEvent = CalendarEvent(
        title = title,
        startsAt = Instant.parse("2026-09-28T00:00:00Z"),
        endsAt = Instant.parse("2026-09-29T00:00:00Z"),
        isAllDay = true,
    )

    private class MutableCalendarRepository(
        var result: DataResult<List<CalendarEvent>>,
    ) : com.morningflow.app.core.data.CalendarRepository {
        override suspend fun getRemainingTodayEvents() = result
    }

    private class ThrowingCalendarRepository : com.morningflow.app.core.data.CalendarRepository {
        var throwOnQuery = true

        override suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>> {
            if (throwOnQuery) error("controlled calendar failure")
            val startsAt = Instant.parse("2026-09-28T08:00:00Z")
            return DataResult.Success(
                listOf(
                    CalendarEvent(
                        title = "Recovered",
                        startsAt = startsAt,
                        endsAt = startsAt.plusSeconds(1_800),
                        isAllDay = false,
                    ),
                ),
            )
        }
    }

    private class CountingCalendarRepository(
        private val results: List<DataResult<List<CalendarEvent>>>,
    ) : com.morningflow.app.core.data.CalendarRepository {
        var calls: Int = 0
            private set

        override suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>> {
            calls += 1
            return results.getOrElse(calls - 1) { results.last() }
        }
    }

    private class DelayedTemplateRepository : TemplateRepository {
        private val state = MutableStateFlow<List<RoutineTemplate>>(emptyList())
        override val templates: StateFlow<List<RoutineTemplate>> = state

        fun publish(value: List<RoutineTemplate>) {
            state.value = value
        }

        override suspend fun awaitReady() = Unit
        override suspend fun save(template: RoutineTemplate) = Unit
        override suspend fun delete(templateId: String) = Unit
        override suspend fun setDefault(templateId: String) = Unit
    }
}

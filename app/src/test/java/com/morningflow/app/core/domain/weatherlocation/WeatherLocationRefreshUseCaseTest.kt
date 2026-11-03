package com.morningflow.app.core.domain.weatherlocation

import com.morningflow.app.core.data.location.api.LocationRepository
import com.morningflow.app.core.data.weather.api.WeatherRepository
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.location.LocationSource
import com.morningflow.app.core.model.weather.WeatherCondition
import com.morningflow.app.core.model.weather.WeatherSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherLocationRefreshUseCaseTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC)

    @Test
    fun refreshGetsLocationBeforeRequestingWeather() = runTest {
        val locationRepository = FakeLocationRepository(DataResult.Success(location()))
        val weatherRepository = FakeWeatherRepository(DataResult.Success(weather()))
        val useCase = useCase(locationRepository, weatherRepository)

        val state = useCase.refresh()

        assertTrue(state is WeatherLocationUiState.Ready)
        assertEquals(1, locationRepository.refreshCount)
        assertEquals(1, weatherRepository.refreshCount)
        assertEquals(location(), weatherRepository.lastLocation)
    }

    @Test
    fun locationFailureDoesNotRequestWeatherOrUseCachedLocation() = runTest {
        val locationRepository = FakeLocationRepository(DataResult.Unavailable("TIMEOUT"))
        val weatherRepository = FakeWeatherRepository(DataResult.Success(weather()))
        val useCase = useCase(locationRepository, weatherRepository)

        val state = useCase.refresh()

        assertEquals(
            WeatherLocationUiState.LocationUnavailable(LocationUnavailableReason.TIMEOUT),
            state,
        )
        assertEquals(0, weatherRepository.refreshCount)
        assertEquals(0, weatherRepository.cachedForCount)
    }

    @Test
    fun permissionFailureMapsToPermissionRequired() = runTest {
        val useCase = useCase(
            locationRepository = FakeLocationRepository(
                DataResult.Unavailable("PERMISSION_DENIED"),
            ),
            weatherRepository = FakeWeatherRepository(DataResult.Success(weather())),
        )

        assertEquals(WeatherLocationUiState.PermissionRequired, useCase.refresh())
    }

    @Test
    fun weatherFailureUsesOnlyCacheForTheFreshLocation() = runTest {
        val locationRepository = FakeLocationRepository(DataResult.Success(location()))
        val weatherRepository = FakeWeatherRepository(DataResult.Unavailable("NETWORK"))
        weatherRepository.cached = weather()
        val useCase = useCase(locationRepository, weatherRepository)

        val state = useCase.refresh()

        assertEquals(
            WeatherLocationUiState.ReadyWithStaleWeather(
                location = location(),
                weather = weather(),
                reason = WeatherUnavailableReason.NETWORK,
            ),
            state,
        )
        assertEquals(1, weatherRepository.cachedForCount)

        weatherRepository.cached = weather(locationKey = "-37.80,144.95")
        assertEquals(
            WeatherLocationUiState.WeatherUnavailable(
                location = location(),
                reason = WeatherUnavailableReason.NETWORK,
            ),
            useCase.refresh(),
        )
    }

    @Test
    fun unexpectedLocationFailureDoesNotLeaveLoadingState() = runTest {
        val locationRepository = FakeLocationRepository(DataResult.Success(location())).apply {
            failure = IllegalStateException("location storage failed")
        }
        val coordinator = coordinator(
            locationRepository = locationRepository,
            weatherRepository = FakeWeatherRepository(DataResult.Success(weather())),
        )

        assertEquals(
            WeatherLocationUiState.LocationUnavailable(LocationUnavailableReason.UNKNOWN),
            coordinator.refresh(),
        )
        assertTrue(coordinator.state.value !is WeatherLocationUiState.Loading)
    }

    @Test
    fun unexpectedWeatherFailureBecomesWeatherUnavailable() = runTest {
        val weatherRepository = FakeWeatherRepository(DataResult.Success(weather())).apply {
            failure = IllegalStateException("weather storage failed")
        }
        val coordinator = coordinator(
            locationRepository = FakeLocationRepository(DataResult.Success(location())),
            weatherRepository = weatherRepository,
        )

        assertEquals(
            WeatherLocationUiState.WeatherUnavailable(
                location = location(),
                reason = WeatherUnavailableReason.UNKNOWN,
            ),
            coordinator.refresh(),
        )
        assertTrue(coordinator.state.value !is WeatherLocationUiState.Loading)
    }

    @Test
    fun concurrentRefreshesAreSerialized() = runTest {
        val locationRepository = FakeLocationRepository(DataResult.Success(location()))
        val weatherRepository = FakeWeatherRepository(DataResult.Success(weather()))
        locationRepository.delayMillis = 10L
        weatherRepository.delayMillis = 10L
        val coordinator = coordinator(locationRepository, weatherRepository)

        val first = launch { coordinator.refresh() }
        val second = launch { coordinator.refresh() }
        advanceUntilIdle()

        assertEquals(1, locationRepository.maxConcurrent)
        assertEquals(1, weatherRepository.maxConcurrent)
        assertEquals(WeatherLocationUiState.Ready(location(), weather()), coordinator.state.value)
        first.join()
        second.join()
    }

    @Test
    fun automaticRefreshRunsImmediatelyWhenThereIsNoSuccessfulState() = runTest {
        val locationRepository = FakeLocationRepository(DataResult.Success(location()))
        val weatherRepository = FakeWeatherRepository(DataResult.Success(weather()))
        val coordinator = coordinator(locationRepository, weatherRepository)

        val job = coordinator.startAutoRefresh(backgroundScope)
        runCurrent()

        assertEquals(1, locationRepository.refreshCount)
        assertEquals(1, weatherRepository.refreshCount)
        job.cancel()
    }

    @Test
    fun automaticRefreshRunsAgainAfterConfiguredInterval() = runTest {
        val locationRepository = FakeLocationRepository(DataResult.Success(location()))
        val weatherRepository = FakeWeatherRepository(DataResult.Success(weather()))
        val coordinator = coordinator(locationRepository, weatherRepository)

        val job = coordinator.startAutoRefresh(backgroundScope)
        runCurrent()
        assertEquals(1, locationRepository.refreshCount)

        advanceTimeBy(15 * 60_000L)
        runCurrent()

        assertEquals(2, locationRepository.refreshCount)
        assertEquals(2, weatherRepository.refreshCount)
        job.cancel()
    }

    @Test
    fun refreshPolicyUsesDefaultAndRejectsOutOfRangeIntervals() {
        assertEquals(15L, WeatherRefreshPolicy().intervalMinutes)
        assertTrue(WeatherRefreshPolicy(5).isStale(0L, 5 * 60_000L))
        assertTrue(!WeatherRefreshPolicy(360).isStale(0L, 359 * 60_000L))

        try {
            WeatherRefreshPolicy(4)
            throw AssertionError("Expected an invalid lower interval to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        try {
            WeatherRefreshPolicy(361)
            throw AssertionError("Expected an invalid upper interval to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun coordinatorExposesAndUpdatesRefreshIntervalForUiConfiguration() {
        val coordinator = coordinator(
            locationRepository = FakeLocationRepository(DataResult.Success(location())),
            weatherRepository = FakeWeatherRepository(DataResult.Success(weather())),
        )

        assertEquals(15L, coordinator.refreshIntervalMinutes)
        coordinator.setRefreshIntervalMinutes(30L)
        assertEquals(30L, coordinator.refreshIntervalMinutes)
    }

    private fun useCase(
        locationRepository: FakeLocationRepository,
        weatherRepository: FakeWeatherRepository,
    ) = WeatherLocationRefreshUseCase(
        locationRepository = locationRepository,
        weatherRepository = weatherRepository,
    )

    private fun coordinator(
        locationRepository: FakeLocationRepository,
        weatherRepository: FakeWeatherRepository,
    ) = WeatherLocationCoordinator(
        refreshUseCase = useCase(locationRepository, weatherRepository),
        clock = clock,
    )

    private fun location() = LocationSnapshot(
        latitude = -37.8136,
        longitude = 144.9631,
        accuracyMeters = 120f,
        capturedAtEpochMs = 900_000L,
        district = "Carlton",
        city = "Melbourne",
        adminArea = "Victoria",
        countryCode = "AU",
        displayLabel = "Carlton, Melbourne",
        source = LocationSource.NETWORK,
    )

    private fun weather(locationKey: String = "-37.81,144.96") = WeatherSnapshot(
        locationKey = locationKey,
        locationLabel = "Carlton, Melbourne",
        temperatureCelsius = 12.4,
        apparentTemperatureCelsius = 11.8,
        dailyMaxTemperatureCelsius = 18.0,
        dailyMinTemperatureCelsius = 9.0,
        condition = WeatherCondition.RAIN,
        rainProbability = 70,
        uvIndex = 4.5,
        fetchedAtEpochMs = 1_000_000L,
    )

    private class FakeLocationRepository(
        private val result: DataResult<LocationSnapshot>,
    ) : LocationRepository {
        private val _current = MutableStateFlow<LocationSnapshot?>(null)
        override val current: StateFlow<LocationSnapshot?> = _current
        var refreshCount = 0
        var delayMillis = 0L
        var failure: Exception? = null
        var maxConcurrent = 0
        private var concurrent = 0

        override suspend fun refreshCurrent(): DataResult<LocationSnapshot> {
            failure?.let { throw it }
            refreshCount++
            concurrent++
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            delay(delayMillis)
            concurrent--
            if (result is DataResult.Success) _current.value = result.value
            return result
        }

        override suspend fun getCached(): LocationSnapshot? = _current.value
    }

    private class FakeWeatherRepository(
        private val result: DataResult<WeatherSnapshot>,
    ) : WeatherRepository {
        private val _current = MutableStateFlow<WeatherSnapshot?>(null)
        override val current: StateFlow<WeatherSnapshot?> = _current
        var cached: WeatherSnapshot? = null
        var refreshCount = 0
        var cachedForCount = 0
        var lastLocation: LocationSnapshot? = null
        var delayMillis = 0L
        var failure: Exception? = null
        var maxConcurrent = 0
        private var concurrent = 0

        override suspend fun refresh(location: LocationSnapshot): DataResult<WeatherSnapshot> {
            failure?.let { throw it }
            refreshCount++
            lastLocation = location
            concurrent++
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            delay(delayMillis)
            concurrent--
            if (result is DataResult.Success) _current.value = result.value
            return result
        }

        override suspend fun getCached(): WeatherSnapshot? = cached

        override suspend fun getCachedFor(location: LocationSnapshot): WeatherSnapshot? {
            cachedForCount++
            return cached?.takeIf { it.locationKey == "-37.81,144.96" }
        }
    }
}

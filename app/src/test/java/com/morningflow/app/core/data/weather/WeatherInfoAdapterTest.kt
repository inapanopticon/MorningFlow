package com.morningflow.app.core.data.weather

import com.morningflow.app.core.data.location.api.LocationRepository
import com.morningflow.app.core.data.weather.api.WeatherRepository
import com.morningflow.app.core.data.weather.impl.WeatherInfoAdapter
import com.morningflow.app.core.data.weather.impl.WeatherInfoRepositoryAdapter
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.location.LocationSource
import com.morningflow.app.core.model.weather.WeatherCondition
import com.morningflow.app.core.model.weather.WeatherSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherInfoAdapterTest {
    private val now = 1_000_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val location = LocationSnapshot(
        latitude = -37.8136,
        longitude = 144.9631,
        accuracyMeters = 80f,
        capturedAtEpochMs = now - 1_000,
        district = "Carlton",
        city = "Melbourne",
        adminArea = "Victoria",
        countryCode = "AU",
        displayLabel = "Carlton, Melbourne",
        source = LocationSource.NETWORK,
    )

    @Test
    fun convertsTheSuccessfulLocationWeatherPair() {
        val result = WeatherInfoAdapter.toWeatherInfo(
            location = location,
            weather = weather(
                temperatureCelsius = 12.6,
                rainProbability = 70,
                uvIndex = 5.5,
                condition = WeatherCondition.RAIN,
            ),
        )

        assertEquals(
            DataResult.Success(
                com.morningflow.app.core.model.WeatherInfo(
                    city = "Melbourne",
                    condition = "rainy",
                    temperatureCelsius = 13,
                    rainProbability = 70,
                    uvIndex = 6,
                ),
            ),
            result,
        )
    }

    @Test
    fun missingLegacyReminderFieldDoesNotBecomeZero() {
        val result = WeatherInfoAdapter.toWeatherInfo(
            location = location,
            weather = weather(rainProbability = null),
        )

        assertEquals(DataResult.Unavailable("MISSING_RAIN_PROBABILITY"), result)
    }

    @Test
    fun repositoryAdapterUsesFreshSameLocationCache() = runBlocking {
        val result = WeatherInfoRepositoryAdapter(
            locationRepository = FakeLocationRepository(location),
            weatherRepository = FakeWeatherRepository(weather()),
            clock = clock,
        ).getTodayWeather()

        assertTrue(result is DataResult.Success)
        assertEquals("Melbourne", (result as DataResult.Success).value.city)
    }

    @Test
    fun repositoryAdapterRejectsStaleWeather() = runBlocking {
        val result = WeatherInfoRepositoryAdapter(
            locationRepository = FakeLocationRepository(location),
            weatherRepository = FakeWeatherRepository(weather(fetchedAtEpochMs = now - 15 * 60 * 1_000L - 1)),
            clock = clock,
        ).getTodayWeather()

        assertEquals(DataResult.Unavailable("STALE_WEATHER"), result)
    }

    @Test
    fun repositoryAdapterUsesTheConfiguredRefreshIntervalAsFreshnessWindow() = runBlocking {
        val result = WeatherInfoRepositoryAdapter(
            locationRepository = FakeLocationRepository(location),
            weatherRepository = FakeWeatherRepository(weather(fetchedAtEpochMs = now - 20 * 60 * 1_000L)),
            clock = clock,
            maxWeatherAgeMillis = { 30 * 60 * 1_000L },
        ).getTodayWeather()

        assertTrue(result is DataResult.Success)
    }

    private fun weather(
        temperatureCelsius: Double = 12.0,
        rainProbability: Int? = 40,
        uvIndex: Double? = 4.0,
        condition: WeatherCondition = WeatherCondition.PARTLY_CLOUDY,
        fetchedAtEpochMs: Long = now,
    ) = WeatherSnapshot(
        locationKey = "-37.81,144.96",
        locationLabel = location.displayLabel,
        temperatureCelsius = temperatureCelsius,
        apparentTemperatureCelsius = 11.0,
        dailyMaxTemperatureCelsius = 18.0,
        dailyMinTemperatureCelsius = 9.0,
        condition = condition,
        rainProbability = rainProbability,
        uvIndex = uvIndex,
        fetchedAtEpochMs = fetchedAtEpochMs,
    )

    private class FakeLocationRepository(
        location: LocationSnapshot?,
    ) : LocationRepository {
        private val _current = MutableStateFlow(location)
        override val current: StateFlow<LocationSnapshot?> = _current.asStateFlow()

        override suspend fun refreshCurrent(): DataResult<LocationSnapshot> =
            DataResult.Unavailable("NOT_USED")

        override suspend fun getCached(): LocationSnapshot? = current.value
    }

    private class FakeWeatherRepository(
        private val weather: WeatherSnapshot?,
    ) : WeatherRepository {
        private val _current = MutableStateFlow(weather)
        override val current: StateFlow<WeatherSnapshot?> = _current.asStateFlow()

        override suspend fun refresh(location: LocationSnapshot): DataResult<WeatherSnapshot> =
            DataResult.Unavailable("NOT_USED")

        override suspend fun getCached(): WeatherSnapshot? = current.value

        override suspend fun getCachedFor(location: LocationSnapshot): WeatherSnapshot? =
            current.value?.takeIf { it.locationKey == "-37.81,144.96" }
    }
}

package com.morningflow.app.core.data.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.morningflow.app.core.data.weather.api.WeatherRepository
import com.morningflow.app.core.data.weather.impl.OpenMeteoApi
import com.morningflow.app.core.data.weather.impl.OpenMeteoCurrentDto
import com.morningflow.app.core.data.weather.impl.OpenMeteoForecastDto
import com.morningflow.app.core.data.weather.impl.OpenMeteoWeatherRepository
import com.morningflow.app.core.data.weather.impl.WeatherDataStore
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.location.LocationSource
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class OpenMeteoWeatherRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun successfulRefreshPersistsAndPublishesWeather() = runBlocking {
        val api = FakeOpenMeteoApi(Response.success(validResponse()))
        val repository = repository(api)

        val result = repository.refresh(location())

        assertTrue(result is DataResult.Success)
        val weather = (result as DataResult.Success).value
        assertEquals(weather, repository.current.value)
        assertEquals(weather, repository.getCached())
    }

    @Test
    fun rateLimitIsReturnedAsUnavailable() = runBlocking {
        val errorBody = "rate limited".toResponseBody("text/plain".toMediaType())
        val api = FakeOpenMeteoApi(Response.error(429, errorBody))
        val repository = repository(api)

        assertEquals(
            DataResult.Unavailable("RATE_LIMITED"),
            repository.refresh(location()),
        )
    }

    @Test
    fun serverFailureIsReturnedAsUnavailable() = runBlocking {
        val errorBody = "server failure".toResponseBody("text/plain".toMediaType())
        val api = FakeOpenMeteoApi(Response.error(503, errorBody))
        val repository = repository(api)

        assertEquals(
            DataResult.Unavailable("SERVER"),
            repository.refresh(location()),
        )
    }

    private fun repository(api: OpenMeteoApi): WeatherRepository =
        OpenMeteoWeatherRepository(
            api = api,
            dataStore = WeatherDataStore(
                dataStoreAt(File(temporaryFolder.root, "weather-${api.hashCode()}.preferences_pb")),
            ),
            clock = Clock.fixed(Instant.ofEpochMilli(9999L), ZoneOffset.UTC),
        )

    private fun dataStoreAt(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun validResponse() = OpenMeteoForecastDto(
        timezone = "Australia/Melbourne",
        current = OpenMeteoCurrentDto(
            time = "2026-09-27T07:00",
            temperatureCelsius = 12.4,
            apparentTemperatureCelsius = 11.8,
            weatherCode = 0,
        ),
    )

    private fun location() = LocationSnapshot(
        latitude = -37.8136,
        longitude = 144.9631,
        accuracyMeters = 120f,
        capturedAtEpochMs = 1000L,
        district = "Carlton",
        city = "Melbourne",
        adminArea = "Victoria",
        countryCode = "AU",
        displayLabel = "Carlton, Melbourne",
        source = LocationSource.NETWORK,
    )

    private class FakeOpenMeteoApi(
        private val response: Response<OpenMeteoForecastDto>,
    ) : OpenMeteoApi {
        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            current: String,
            hourly: String,
            daily: String,
            forecastDays: Int,
            timezone: String,
        ): Response<OpenMeteoForecastDto> = response
    }
}

package com.morningflow.app.core.data.weather

import com.morningflow.app.core.data.weather.impl.OpenMeteoCurrentDto
import com.morningflow.app.core.data.weather.impl.OpenMeteoDailyDto
import com.morningflow.app.core.data.weather.impl.OpenMeteoForecastDto
import com.morningflow.app.core.data.weather.impl.OpenMeteoHourlyDto
import com.morningflow.app.core.data.weather.impl.OpenMeteoMapper
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.location.LocationSource
import com.morningflow.app.core.model.weather.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoMapperTest {
    @Test
    fun mapsCurrentAndDailyValuesToDomainSnapshot() {
        val result = OpenMeteoMapper.map(
            dto = OpenMeteoForecastDto(
                timezone = "Australia/Melbourne",
                current = OpenMeteoCurrentDto(
                    time = "2026-09-27T07:35",
                    temperatureCelsius = 12.4,
                    apparentTemperatureCelsius = 11.8,
                    weatherCode = 61,
                ),
                hourly = OpenMeteoHourlyDto(
                    time = listOf("2026-09-27T07:00", "2026-09-27T08:00"),
                    precipitationProbability = listOf(70, 80),
                ),
                daily = OpenMeteoDailyDto(
                    time = listOf("2026-09-27", "2026-09-28"),
                    temperatureMaxCelsius = listOf(18.0, 19.0),
                    temperatureMinCelsius = listOf(9.0, 10.0),
                    uvIndexMax = listOf(4.5, 5.0),
                ),
            ),
            location = melbourneLocation(),
            fetchedAtEpochMs = 1234L,
        )

        assertTrue(result is DataResult.Success)
        val weather = (result as DataResult.Success).value
        assertEquals("-37.81,144.96", weather.locationKey)
        assertEquals(12.4, weather.temperatureCelsius, 0.001)
        assertEquals(18.0, weather.dailyMaxTemperatureCelsius!!, 0.001)
        assertEquals(9.0, weather.dailyMinTemperatureCelsius!!, 0.001)
        assertEquals(70, weather.rainProbability)
        assertEquals(4.5, weather.uvIndex!!, 0.001)
        assertEquals(WeatherCondition.RAIN, weather.condition)
    }

    @Test
    fun missingCurrentTemperatureIsUnavailable() {
        val result = OpenMeteoMapper.map(
            dto = OpenMeteoForecastDto(
                timezone = "Australia/Melbourne",
                current = OpenMeteoCurrentDto(
                    time = "2026-09-27T07:00",
                    temperatureCelsius = null,
                    weatherCode = 0,
                ),
            ),
            location = melbourneLocation(),
            fetchedAtEpochMs = 1234L,
        )

        assertEquals(DataResult.Unavailable("INVALID_RESPONSE"), result)
    }

    private fun melbourneLocation() = LocationSnapshot(
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
}

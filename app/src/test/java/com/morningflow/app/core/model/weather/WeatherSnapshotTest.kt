package com.morningflow.app.core.model.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherSnapshotTest {
    @Test
    fun keepsTemperaturePrecisionAndDerivesFreshnessElsewhere() {
        val snapshot = WeatherSnapshot(
            locationKey = "-37.81,144.96",
            locationLabel = "Melbourne",
            temperatureCelsius = 18.5,
            apparentTemperatureCelsius = 18.0,
            dailyMaxTemperatureCelsius = 22.4,
            dailyMinTemperatureCelsius = 11.2,
            condition = WeatherCondition.PARTLY_CLOUDY,
            rainProbability = 20,
            uvIndex = 4.0,
            fetchedAtEpochMs = 123L,
        )

        assertEquals(18.5, snapshot.temperatureCelsius, 0.0)
        assertEquals(123L, snapshot.fetchedAtEpochMs)
    }
}

package com.morningflow.app.core.data.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.morningflow.app.core.data.weather.impl.WeatherDataStore
import com.morningflow.app.core.model.weather.WeatherCondition
import com.morningflow.app.core.model.weather.WeatherSnapshot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WeatherDataStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun latestWeatherRoundTripsThroughPreferences() = runBlocking {
        val store = WeatherDataStore(dataStoreAt(File(temporaryFolder.root, "weather.preferences_pb")))
        val expected = WeatherSnapshot(
            locationKey = "-37.81,144.96",
            locationLabel = "Carlton, Melbourne",
            temperatureCelsius = 12.4,
            apparentTemperatureCelsius = 11.8,
            dailyMaxTemperatureCelsius = 18.0,
            dailyMinTemperatureCelsius = 9.0,
            condition = WeatherCondition.RAIN,
            rainProbability = 70,
            uvIndex = 4.5,
            fetchedAtEpochMs = 1234L,
        )

        store.save(expected)

        assertEquals(expected, store.get())
    }

    private fun dataStoreAt(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
}

package com.morningflow.app.core.data.weather.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.morningflow.app.core.model.weather.WeatherCondition
import com.morningflow.app.core.model.weather.WeatherSnapshot
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

class WeatherDataStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun save(snapshot: WeatherSnapshot) {
        dataStore.edit { preferences ->
            preferences[SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
            preferences[LOCATION_KEY] = snapshot.locationKey
            preferences[LOCATION_LABEL] = snapshot.locationLabel
            preferences[TEMPERATURE] = snapshot.temperatureCelsius.toString()
            snapshot.apparentTemperatureCelsius.put(preferences, APPARENT_TEMPERATURE)
            snapshot.dailyMaxTemperatureCelsius.put(preferences, DAILY_MAX_TEMPERATURE)
            snapshot.dailyMinTemperatureCelsius.put(preferences, DAILY_MIN_TEMPERATURE)
            preferences[CONDITION] = snapshot.condition.name
            snapshot.rainProbability.put(preferences, RAIN_PROBABILITY)
            snapshot.uvIndex.put(preferences, UV_INDEX)
            preferences[FETCHED_AT] = snapshot.fetchedAtEpochMs.toString()
        }
    }

    suspend fun get(): WeatherSnapshot? = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .first()
        .toWeatherSnapshot()

    private fun Preferences.toWeatherSnapshot(): WeatherSnapshot? {
        if (this[SCHEMA_VERSION] != CURRENT_SCHEMA_VERSION) return null
        val locationKey = this[LOCATION_KEY] ?: return null
        val locationLabel = this[LOCATION_LABEL] ?: return null
        val temperature = this[TEMPERATURE]?.toDoubleOrNull() ?: return null
        val condition = this[CONDITION]
            ?.let { value -> runCatching { WeatherCondition.valueOf(value) }.getOrNull() }
            ?: return null
        val fetchedAt = this[FETCHED_AT]?.toLongOrNull() ?: return null

        return runCatching {
            WeatherSnapshot(
                locationKey = locationKey,
                locationLabel = locationLabel,
                temperatureCelsius = temperature,
                apparentTemperatureCelsius = this[APPARENT_TEMPERATURE]?.toDoubleOrNull(),
                dailyMaxTemperatureCelsius = this[DAILY_MAX_TEMPERATURE]?.toDoubleOrNull(),
                dailyMinTemperatureCelsius = this[DAILY_MIN_TEMPERATURE]?.toDoubleOrNull(),
                condition = condition,
                rainProbability = this[RAIN_PROBABILITY]?.toIntOrNull(),
                uvIndex = this[UV_INDEX]?.toDoubleOrNull(),
                fetchedAtEpochMs = fetchedAt,
            )
        }.getOrNull()
    }

    private fun Any?.put(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<String>,
    ) {
        if (this == null) preferences.remove(key) else preferences[key] = toString()
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val SCHEMA_VERSION = intPreferencesKey("schema_version")
        val LOCATION_KEY = stringPreferencesKey("location_key")
        val LOCATION_LABEL = stringPreferencesKey("location_label")
        val TEMPERATURE = stringPreferencesKey("temperature_celsius")
        val APPARENT_TEMPERATURE = stringPreferencesKey("apparent_temperature_celsius")
        val DAILY_MAX_TEMPERATURE = stringPreferencesKey("daily_max_temperature_celsius")
        val DAILY_MIN_TEMPERATURE = stringPreferencesKey("daily_min_temperature_celsius")
        val CONDITION = stringPreferencesKey("condition")
        val RAIN_PROBABILITY = stringPreferencesKey("rain_probability")
        val UV_INDEX = stringPreferencesKey("uv_index")
        val FETCHED_AT = stringPreferencesKey("fetched_at_epoch_ms")
    }
}

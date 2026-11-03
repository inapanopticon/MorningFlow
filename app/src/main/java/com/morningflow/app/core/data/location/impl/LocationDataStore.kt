package com.morningflow.app.core.data.location.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.location.LocationSource
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

class LocationDataStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun save(snapshot: LocationSnapshot) {
        dataStore.edit { preferences ->
            preferences[SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
            preferences[LATITUDE] = snapshot.latitude.toString()
            preferences[LONGITUDE] = snapshot.longitude.toString()
            snapshot.accuracyMeters?.let { preferences[ACCURACY_METERS] = it.toString() }
                ?: preferences.remove(ACCURACY_METERS)
            preferences[CAPTURED_AT_EPOCH_MS] = snapshot.capturedAtEpochMs.toString()
            snapshot.district?.let { preferences[DISTRICT] = it } ?: preferences.remove(DISTRICT)
            snapshot.city?.let { preferences[CITY] = it } ?: preferences.remove(CITY)
            snapshot.adminArea?.let { preferences[ADMIN_AREA] = it }
                ?: preferences.remove(ADMIN_AREA)
            snapshot.countryCode?.let { preferences[COUNTRY_CODE] = it }
                ?: preferences.remove(COUNTRY_CODE)
            preferences[DISPLAY_LABEL] = snapshot.displayLabel
            preferences[SOURCE] = snapshot.source.name
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    suspend fun get(): LocationSnapshot? = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .first()
        .toLocationSnapshot()

    private fun Preferences.toLocationSnapshot(): LocationSnapshot? {
        if (this[SCHEMA_VERSION] != CURRENT_SCHEMA_VERSION) return null
        val latitude = this[LATITUDE]?.toDoubleOrNull() ?: return null
        val longitude = this[LONGITUDE]?.toDoubleOrNull() ?: return null
        val capturedAt = this[CAPTURED_AT_EPOCH_MS]?.toLongOrNull() ?: return null
        val displayLabel = this[DISPLAY_LABEL] ?: return null
        val source = this[SOURCE]
            ?.let { value -> runCatching { LocationSource.valueOf(value) }.getOrNull() }
            ?: LocationSource.UNKNOWN

        return runCatching {
            LocationSnapshot(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = this[ACCURACY_METERS]?.toFloatOrNull(),
                capturedAtEpochMs = capturedAt,
                district = this[DISTRICT],
                city = this[CITY],
                adminArea = this[ADMIN_AREA],
                countryCode = this[COUNTRY_CODE],
                displayLabel = displayLabel,
                source = source,
            )
        }.getOrNull()
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val SCHEMA_VERSION = intPreferencesKey("schema_version")
        val LATITUDE = stringPreferencesKey("latitude")
        val LONGITUDE = stringPreferencesKey("longitude")
        val ACCURACY_METERS = stringPreferencesKey("accuracy_meters")
        val CAPTURED_AT_EPOCH_MS = stringPreferencesKey("captured_at_epoch_ms")
        val DISTRICT = stringPreferencesKey("district")
        val CITY = stringPreferencesKey("city")
        val ADMIN_AREA = stringPreferencesKey("admin_area")
        val COUNTRY_CODE = stringPreferencesKey("country_code")
        val DISPLAY_LABEL = stringPreferencesKey("display_label")
        val SOURCE = stringPreferencesKey("source")
    }
}

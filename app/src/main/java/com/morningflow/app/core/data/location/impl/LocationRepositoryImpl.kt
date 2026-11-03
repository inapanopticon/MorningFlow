package com.morningflow.app.core.data.location.impl

import com.morningflow.app.core.data.location.api.LocationRepository
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationRepositoryImpl(
    private val source: AndroidLocationSource,
    private val reverseGeocoder: AndroidReverseGeocoder,
    private val dataStore: LocationDataStore,
    /** A city picked by hand; when present it is used instead of asking the device. */
    private val manualCity: suspend () -> LocationSnapshot? = { null },
) : LocationRepository {
    private val _current = MutableStateFlow<LocationSnapshot?>(null)
    override val current: StateFlow<LocationSnapshot?> = _current.asStateFlow()

    override suspend fun refreshCurrent(): DataResult<LocationSnapshot> {
        manualCity()?.let { city ->
            _current.value = city
            return DataResult.Success(city)
        }
        return when (val result = source.getCurrent()) {
            is DataResult.Unavailable -> result
            is DataResult.Success -> {
                val reading = result.value
                val address = reverseGeocoder.resolve(reading.latitude, reading.longitude)
                val snapshot = LocationSnapshot(
                    latitude = reading.latitude,
                    longitude = reading.longitude,
                    accuracyMeters = reading.accuracyMeters,
                    capturedAtEpochMs = reading.capturedAtEpochMs,
                    district = address?.district,
                    city = address?.city,
                    adminArea = address?.adminArea,
                    countryCode = address?.countryCode,
                    displayLabel = LocationLabelFormatter.format(
                        address = address,
                        latitude = reading.latitude,
                        longitude = reading.longitude,
                    ),
                    source = reading.source,
                )
                dataStore.save(snapshot)
                _current.value = snapshot
                DataResult.Success(snapshot)
            }
        }
    }

    override suspend fun getCached(): LocationSnapshot? = (manualCity() ?: dataStore.get()).also { cached ->
        if (cached != null) _current.value = cached
    }
}

package com.morningflow.app.core.domain.runtime

import com.morningflow.app.core.data.location.api.LocationRepository
import com.morningflow.app.core.data.weather.api.WeatherRepository
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.*
import com.morningflow.app.core.model.weather.WeatherSnapshot
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SessionWeatherLocationLoaderTest {
    private val position = LocationSnapshot(-37.8, 144.9, null, 0, null, "Melbourne", null, "AU", "Melbourne", LocationSource.NETWORK)
    private class Location(private val read: suspend () -> DataResult<LocationSnapshot>) : LocationRepository {
        override val current = MutableStateFlow<LocationSnapshot?>(null)
        override suspend fun refreshCurrent() = read()
        override suspend fun getCached(): LocationSnapshot? = null
    }
    private class Weather(private val read: suspend (LocationSnapshot) -> DataResult<WeatherSnapshot>) : WeatherRepository {
        override val current = MutableStateFlow<WeatherSnapshot?>(null)
        override suspend fun refresh(location: LocationSnapshot) = read(location)
        override suspend fun getCached(): WeatherSnapshot? = error("must refresh")
        override suspend fun getCachedFor(location: LocationSnapshot): WeatherSnapshot? = error("must refresh")
    }
    @Test fun waitsBeyondFiveSecondsThenRefreshesWeatherForNewLocation() = runTest {
        var refreshed = false
        val result = SessionWeatherLocationLoader(Location { delay(6_000); DataResult.Success(position) }, Weather {
            assertEquals(position, it)
            refreshed = true
            DataResult.Unavailable("NETWORK")
        }).load()
        assertTrue(refreshed)
        assertEquals(DataResult.Success("Melbourne"), result.first)
        assertEquals(DataResult.Unavailable("NETWORK"), result.second)
    }
    @Test fun locationFailurePreservesReasonAndDoesNotRequestWeather() = runTest {
        val result = SessionWeatherLocationLoader(Location { DataResult.Unavailable("TIMEOUT") }, Weather { error("not reached") }).load()
        assertEquals(DataResult.Unavailable("TIMEOUT"), result.first)
        assertEquals(DataResult.Unavailable("TIMEOUT"), result.second)
    }
    @Test fun weatherTimeoutKeepsSuccessfulLocation() = runTest {
        val result = SessionWeatherLocationLoader(Location { DataResult.Success(position) }, Weather { awaitCancellation() }, weatherTimeoutMillis = 100).load()
        assertEquals(DataResult.Success("Melbourne"), result.first)
        assertEquals(DataResult.Unavailable("TIMEOUT"), result.second)
    }
}

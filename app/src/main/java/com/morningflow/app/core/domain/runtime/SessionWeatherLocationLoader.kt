package com.morningflow.app.core.domain.runtime

import com.morningflow.app.core.data.location.api.LocationRepository
import com.morningflow.app.core.data.weather.api.WeatherRepository
import com.morningflow.app.core.data.weather.impl.WeatherInfoAdapter
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.WeatherInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Session-only loading; Calendar runs independently and leaving the screen cancels requests. */
class SessionWeatherLocationLoader(
    private val location: LocationRepository,
    private val weather: WeatherRepository,
    private val locationTimeoutMillis: Long = 15_000,
    private val weatherTimeoutMillis: Long = 20_000,
) {
    suspend fun load(): Pair<DataResult<String>, DataResult<WeatherInfo>> {
        val position = fetch(locationTimeoutMillis) { location.refreshCurrent() }
        if (position is DataResult.Unavailable) return position to position
        val snapshot = (position as DataResult.Success).value
        val forecast = fetch(weatherTimeoutMillis) { weather.refresh(snapshot) }
        val info = when (forecast) {
            is DataResult.Success -> WeatherInfoAdapter.toWeatherInfo(snapshot, forecast.value)
            is DataResult.Unavailable -> forecast
        }
        return DataResult.Success(snapshot.displayLabel) to info
    }

    private suspend fun <T> fetch(timeout: Long, block: suspend () -> DataResult<T>): DataResult<T> = try {
        withTimeoutOrNull(timeout) { block() } ?: DataResult.Unavailable("TIMEOUT")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DataResult.Unavailable("QUERY_FAILED")
    }
}

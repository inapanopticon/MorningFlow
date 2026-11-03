package com.morningflow.app.core.data.weather.impl

import com.morningflow.app.core.data.WeatherRepository as LegacyWeatherRepository
import com.morningflow.app.core.data.location.api.LocationRepository
import com.morningflow.app.core.data.weather.api.WeatherRepository
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.WeatherInfo
import java.time.Clock
import kotlinx.coroutines.CancellationException

/**
 * Bridges the production weather/location repositories to the legacy SessionEngine
 * contract without making SessionEngine depend on the new feature state or APIs.
 *
 * This adapter reads only the latest successful location and same-location weather
 * cache. It never starts a network request while narration is being assembled.
 */
class WeatherInfoRepositoryAdapter(
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val maxWeatherAgeMillis: () -> Long = { DEFAULT_MAX_WEATHER_AGE_MILLIS },
) : LegacyWeatherRepository {
    override suspend fun getTodayWeather(): DataResult<WeatherInfo> {
        val location = locationRepository.current.value
            ?: return DataResult.Unavailable(LOCATION_UNAVAILABLE)
        val weather = try {
            weatherRepository.getCachedFor(location)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return DataResult.Unavailable(WEATHER_UNAVAILABLE)

        val weatherAgeMillis = (clock.millis() - weather.fetchedAtEpochMs).coerceAtLeast(0L)
        val allowedWeatherAgeMillis = maxWeatherAgeMillis()
        require(allowedWeatherAgeMillis > 0) { "maxWeatherAgeMillis must be positive" }
        if (weatherAgeMillis > allowedWeatherAgeMillis) {
            return DataResult.Unavailable(STALE_WEATHER)
        }

        return WeatherInfoAdapter.toWeatherInfo(location, weather)
    }

    private companion object {
        const val DEFAULT_MAX_WEATHER_AGE_MILLIS = 15 * 60 * 1_000L
        const val LOCATION_UNAVAILABLE = "LOCATION_UNAVAILABLE"
        const val WEATHER_UNAVAILABLE = "WEATHER_UNAVAILABLE"
        const val STALE_WEATHER = "STALE_WEATHER"
    }
}

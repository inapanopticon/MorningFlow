package com.morningflow.app.core.domain.weatherlocation

import com.morningflow.app.core.data.location.api.LocationRepository
import com.morningflow.app.core.data.weather.api.WeatherRepository
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSnapshot
import kotlinx.coroutines.CancellationException

/** Executes one location-then-weather refresh without owning UI state or a timer. */
class WeatherLocationRefreshUseCase(
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
) {

    /** Executes one refresh using a newly obtained location. */
    suspend fun refresh(): WeatherLocationUiState {
        val locationResult = try {
            locationRepository.refreshCurrent()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            DataResult.Unavailable(UNKNOWN_REASON)
        }
        return when (locationResult) {
            is DataResult.Unavailable -> mapLocationFailure(locationResult.reason)
            is DataResult.Success -> refreshWeather(locationResult.value)
        }
    }

    private suspend fun refreshWeather(location: LocationSnapshot): WeatherLocationUiState {
        val weatherResult = try {
            weatherRepository.refresh(location)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            DataResult.Unavailable(UNKNOWN_REASON)
        }
        val nextState = when (weatherResult) {
            is DataResult.Success -> WeatherLocationUiState.Ready(
                location = location,
                weather = weatherResult.value,
            )

            is DataResult.Unavailable -> getCachedWeather(location)?.let { cached ->
                WeatherLocationUiState.ReadyWithStaleWeather(
                    location = location,
                    weather = cached,
                    reason = weatherResult.reason.toWeatherUnavailableReason(),
                )
            } ?: WeatherLocationUiState.WeatherUnavailable(
                location = location,
                reason = weatherResult.reason.toWeatherUnavailableReason(),
            )
        }
        return nextState
    }

    private fun mapLocationFailure(reason: String): WeatherLocationUiState {
        return if (reason == PERMISSION_DENIED_REASON) {
            WeatherLocationUiState.PermissionRequired
        } else {
            WeatherLocationUiState.LocationUnavailable(reason.toLocationUnavailableReason())
        }
    }

    private suspend fun getCachedWeather(location: LocationSnapshot) = try {
        weatherRepository.getCachedFor(location)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val PERMISSION_DENIED_REASON = "PERMISSION_DENIED"
        const val UNKNOWN_REASON = "UNKNOWN"
    }
}

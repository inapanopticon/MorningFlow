package com.morningflow.app.core.domain.weatherlocation

import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.weather.WeatherSnapshot

/** Domain-level location failures safe for UI and application integration code. */
enum class LocationUnavailableReason {
    SERVICE_DISABLED,
    PROVIDER_UNAVAILABLE,
    TIMEOUT,
    UNKNOWN,
}

/** Domain-level weather failures safe for UI and application integration code. */
enum class WeatherUnavailableReason {
    TIMEOUT,
    NETWORK,
    RATE_LIMITED,
    SERVER,
    HTTP,
    EMPTY_RESPONSE,
    INVALID_RESPONSE,
    UNKNOWN,
}

/** The states the weather/location UI can render without knowing data-layer details. */
sealed interface WeatherLocationUiState {
    data object Idle : WeatherLocationUiState

    data object Loading : WeatherLocationUiState

    data class Ready(
        val location: LocationSnapshot,
        val weather: WeatherSnapshot,
    ) : WeatherLocationUiState

    data object PermissionRequired : WeatherLocationUiState

    data class LocationUnavailable(
        val reason: LocationUnavailableReason,
    ) : WeatherLocationUiState

    data class WeatherUnavailable(
        val location: LocationSnapshot,
        val reason: WeatherUnavailableReason,
    ) : WeatherLocationUiState

    data class ReadyWithStaleWeather(
        val location: LocationSnapshot,
        val weather: WeatherSnapshot,
        val reason: WeatherUnavailableReason,
    ) : WeatherLocationUiState
}

internal fun String.toLocationUnavailableReason(): LocationUnavailableReason = when (this) {
    "SERVICE_DISABLED" -> LocationUnavailableReason.SERVICE_DISABLED
    "PROVIDER_UNAVAILABLE" -> LocationUnavailableReason.PROVIDER_UNAVAILABLE
    "TIMEOUT" -> LocationUnavailableReason.TIMEOUT
    "PERMISSION_DENIED" -> LocationUnavailableReason.UNKNOWN
    "FAILED" -> LocationUnavailableReason.UNKNOWN
    else -> LocationUnavailableReason.UNKNOWN
}

internal fun String.toWeatherUnavailableReason(): WeatherUnavailableReason = when (this) {
    "TIMEOUT" -> WeatherUnavailableReason.TIMEOUT
    "NETWORK" -> WeatherUnavailableReason.NETWORK
    "RATE_LIMITED" -> WeatherUnavailableReason.RATE_LIMITED
    "SERVER" -> WeatherUnavailableReason.SERVER
    "HTTP" -> WeatherUnavailableReason.HTTP
    "EMPTY_RESPONSE" -> WeatherUnavailableReason.EMPTY_RESPONSE
    "INVALID_RESPONSE" -> WeatherUnavailableReason.INVALID_RESPONSE
    else -> WeatherUnavailableReason.UNKNOWN
}

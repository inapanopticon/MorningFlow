package com.morningflow.app.core.data.weather.impl

/** Failure categories used inside the weather data implementation. */
internal enum class WeatherFailureReason {
    TIMEOUT,
    NETWORK,
    RATE_LIMITED,
    SERVER,
    HTTP,
    EMPTY_RESPONSE,
    INVALID_RESPONSE,
}

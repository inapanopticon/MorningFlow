package com.morningflow.app.core.data.weather.impl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoForecastDto(
    val timezone: String? = null,
    val current: OpenMeteoCurrentDto? = null,
    val hourly: OpenMeteoHourlyDto? = null,
    val daily: OpenMeteoDailyDto? = null,
)

@Serializable
data class OpenMeteoCurrentDto(
    val time: String? = null,
    @SerialName("temperature_2m") val temperatureCelsius: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperatureCelsius: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
)

@Serializable
data class OpenMeteoHourlyDto(
    val time: List<String> = emptyList(),
    @SerialName("precipitation_probability")
    val precipitationProbability: List<Int?> = emptyList(),
)

@Serializable
data class OpenMeteoDailyDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMaxCelsius: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMinCelsius: List<Double?> = emptyList(),
    @SerialName("uv_index_max") val uvIndexMax: List<Double?> = emptyList(),
)

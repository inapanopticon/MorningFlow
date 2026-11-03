package com.morningflow.app.core.data.weather.impl

import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.WeatherInfo
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.weather.WeatherCondition
import com.morningflow.app.core.model.weather.WeatherSnapshot
import kotlin.math.roundToInt

/** Converts the new location/weather pair into the legacy SessionEngine weather model. */
object WeatherInfoAdapter {
    fun toWeatherInfo(
        location: LocationSnapshot,
        weather: WeatherSnapshot,
    ): DataResult<WeatherInfo> {
        val rainProbability = weather.rainProbability
            ?: return DataResult.Unavailable(MISSING_RAIN_PROBABILITY)
        val uvIndex = weather.uvIndex
            ?: return DataResult.Unavailable(MISSING_UV_INDEX)

        return DataResult.Success(
            WeatherInfo(
                city = locationCity(location),
                condition = weather.condition.toNarrationText(),
                temperatureCelsius = weather.temperatureCelsius.roundToInt(),
                rainProbability = rainProbability,
                uvIndex = uvIndex.roundToInt(),
            ),
        )
    }

    private fun locationCity(location: LocationSnapshot): String =
        sequenceOf(location.city, location.district, location.adminArea)
            .filterNotNull()
            .firstOrNull { it.isNotBlank() }
            ?: location.displayLabel.ifBlank { "your location" }

    private fun WeatherCondition.toNarrationText(): String = when (this) {
        WeatherCondition.CLEAR -> "clear skies"
        WeatherCondition.PARTLY_CLOUDY -> "partly cloudy"
        WeatherCondition.OVERCAST -> "overcast"
        WeatherCondition.FOG -> "foggy"
        WeatherCondition.DRIZZLE -> "drizzly"
        WeatherCondition.RAIN -> "rainy"
        WeatherCondition.SNOW -> "snowy"
        WeatherCondition.THUNDERSTORM -> "stormy"
        WeatherCondition.UNKNOWN -> "unknown conditions"
    }

    private const val MISSING_RAIN_PROBABILITY = "MISSING_RAIN_PROBABILITY"
    private const val MISSING_UV_INDEX = "MISSING_UV_INDEX"
}

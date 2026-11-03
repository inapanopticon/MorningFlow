package com.morningflow.app.core.model.weather

/** The small set of weather facts needed by the MorningFlow home experience. */
data class WeatherSnapshot(
    val locationKey: String,
    val locationLabel: String,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double?,
    val dailyMaxTemperatureCelsius: Double?,
    val dailyMinTemperatureCelsius: Double?,
    val condition: WeatherCondition,
    val rainProbability: Int?,
    val uvIndex: Double?,
    val fetchedAtEpochMs: Long,
) {
    init {
        require(locationKey.isNotBlank()) { "locationKey must not be blank" }
        require(rainProbability == null || rainProbability in 0..100) {
            "rainProbability must be between 0 and 100"
        }
        require(uvIndex == null || uvIndex >= 0.0) { "uvIndex must be non-negative" }
    }
}

/** Domain-level weather condition; API-specific numeric codes stay outside the domain. */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDERSTORM,
    UNKNOWN,
}

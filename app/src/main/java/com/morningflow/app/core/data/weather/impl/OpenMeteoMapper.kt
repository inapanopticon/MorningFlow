package com.morningflow.app.core.data.weather.impl

import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.weather.WeatherCondition
import com.morningflow.app.core.model.weather.WeatherSnapshot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object OpenMeteoMapper {
    fun map(
        dto: OpenMeteoForecastDto,
        location: LocationSnapshot,
        fetchedAtEpochMs: Long,
    ): DataResult<WeatherSnapshot> {
        val current = dto.current ?: return unavailable(WeatherFailureReason.INVALID_RESPONSE)
        val temperature = current.temperatureCelsius
            ?: return unavailable(WeatherFailureReason.INVALID_RESPONSE)
        val weatherCode = current.weatherCode
            ?: return unavailable(WeatherFailureReason.INVALID_RESPONSE)
        val timezone = runCatching { ZoneId.of(dto.timezone ?: "UTC") }
            .getOrDefault(ZoneId.of("UTC"))
        val currentLocalTime = parseLocalDateTime(current.time, timezone)
            ?: return unavailable(WeatherFailureReason.INVALID_RESPONSE)
        val currentDate = currentLocalTime.toLocalDate()
        val dailyIndex = dto.daily?.time?.indexOf(currentDate.toString()) ?: -1
        val hourlyIndex = dto.hourly?.time?.indexOfFirst { value ->
            parseLocalDateTime(value, timezone)
                ?.truncatedTo(ChronoUnit.HOURS) == currentLocalTime.truncatedTo(ChronoUnit.HOURS)
        } ?: -1

        val daily = dto.daily
        val weather = runCatching {
            WeatherSnapshot(
                locationKey = LocationKeyFormatter.from(location),
                locationLabel = location.displayLabel,
                temperatureCelsius = temperature,
                apparentTemperatureCelsius = current.apparentTemperatureCelsius,
                dailyMaxTemperatureCelsius = daily?.temperatureMaxCelsius?.getOrNull(dailyIndex),
                dailyMinTemperatureCelsius = daily?.temperatureMinCelsius?.getOrNull(dailyIndex),
                condition = weatherCondition(weatherCode),
                rainProbability = dto.hourly?.precipitationProbability?.getOrNull(hourlyIndex),
                uvIndex = daily?.uvIndexMax?.getOrNull(dailyIndex),
                fetchedAtEpochMs = fetchedAtEpochMs,
            )
        }.getOrElse {
            return unavailable(WeatherFailureReason.INVALID_RESPONSE)
        }

        return DataResult.Success(weather)
    }

    private fun parseLocalDateTime(value: String?, timezone: ZoneId): LocalDateTime? {
        if (value == null) return null
        return runCatching { OffsetDateTime.parse(value).toLocalDateTime() }
            .recoverCatching { ZonedDateTime.parse(value).withZoneSameInstant(timezone).toLocalDateTime() }
            .recoverCatching { LocalDateTime.parse(value) }
            .getOrNull()
    }

    private fun weatherCondition(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR
        1, 2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.OVERCAST
        45, 48 -> WeatherCondition.FOG
        51, 53, 55, 56, 57 -> WeatherCondition.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
        95, 96, 99 -> WeatherCondition.THUNDERSTORM
        else -> WeatherCondition.UNKNOWN
    }

    private fun unavailable(reason: WeatherFailureReason): DataResult<Nothing> =
        DataResult.Unavailable(reason.name)
}

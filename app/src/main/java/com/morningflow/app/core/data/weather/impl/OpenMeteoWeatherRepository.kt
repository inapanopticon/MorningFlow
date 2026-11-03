package com.morningflow.app.core.data.weather.impl

import com.morningflow.app.core.data.weather.api.WeatherRepository
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.weather.WeatherSnapshot
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException

class OpenMeteoWeatherRepository(
    private val api: OpenMeteoApi,
    private val dataStore: WeatherDataStore,
    private val clock: Clock = Clock.systemUTC(),
) : WeatherRepository {
    private val _current = MutableStateFlow<WeatherSnapshot?>(null)
    override val current: StateFlow<WeatherSnapshot?> = _current.asStateFlow()

    override suspend fun refresh(location: LocationSnapshot): DataResult<WeatherSnapshot> {
        return try {
            val response = api.getForecast(
                latitude = location.latitude,
                longitude = location.longitude,
                current = CURRENT_FIELDS,
                hourly = HOURLY_FIELDS,
                daily = DAILY_FIELDS,
                forecastDays = 2,
                timezone = "auto",
            )
            if (!response.isSuccessful) {
                DataResult.Unavailable(
                    when {
                        response.code() == 429 -> WeatherFailureReason.RATE_LIMITED.name
                        response.code() >= 500 -> WeatherFailureReason.SERVER.name
                        else -> WeatherFailureReason.HTTP.name
                    },
                )
            } else {
                val body = response.body()
                    ?: return DataResult.Unavailable(WeatherFailureReason.EMPTY_RESPONSE.name)
                when (val mapped = OpenMeteoMapper.map(body, location, clock.millis())) {
                    is DataResult.Unavailable -> mapped
                    is DataResult.Success -> {
                        dataStore.save(mapped.value)
                        _current.value = mapped.value
                        mapped
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            DataResult.Unavailable(WeatherFailureReason.TIMEOUT.name)
        } catch (_: IOException) {
            DataResult.Unavailable(WeatherFailureReason.NETWORK.name)
        } catch (_: HttpException) {
            DataResult.Unavailable(WeatherFailureReason.HTTP.name)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            DataResult.Unavailable(WeatherFailureReason.NETWORK.name)
        }
    }

    override suspend fun getCached(): WeatherSnapshot? = dataStore.get().also { cached ->
        if (cached != null) _current.value = cached
    }

    override suspend fun getCachedFor(location: LocationSnapshot): WeatherSnapshot? =
        dataStore.get()
            ?.takeIf { cached -> cached.locationKey == LocationKeyFormatter.from(location) }
            ?.also { cached -> _current.value = cached }

    private companion object {
        const val CURRENT_FIELDS = "temperature_2m,apparent_temperature,weather_code"
        const val HOURLY_FIELDS = "precipitation_probability"
        const val DAILY_FIELDS = "temperature_2m_max,temperature_2m_min,uv_index_max"
    }
}

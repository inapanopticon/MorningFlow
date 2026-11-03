package com.morningflow.app.core.data.weather.api

import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.weather.WeatherSnapshot
import kotlinx.coroutines.flow.StateFlow

interface WeatherRepository {
    val current: StateFlow<WeatherSnapshot?>

    suspend fun refresh(location: LocationSnapshot): DataResult<WeatherSnapshot>

    suspend fun getCached(): WeatherSnapshot?

    suspend fun getCachedFor(location: LocationSnapshot): WeatherSnapshot?
}

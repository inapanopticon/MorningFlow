package com.morningflow.app.core.data.weather.impl

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String,
        @Query("hourly") hourly: String,
        @Query("daily") daily: String,
        @Query("forecast_days") forecastDays: Int,
        @Query("timezone") timezone: String,
    ): Response<OpenMeteoForecastDto>
}

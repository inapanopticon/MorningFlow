package com.morningflow.app.core.data.location.impl

import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.location.LocationSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** Open-Meteo's keyless place search, used when the user picks a city by name. */
interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 6,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
    ): GeocodingResponse
}

@Serializable
data class GeocodingResponse(val results: List<GeocodingPlace> = emptyList())

@Serializable
data class GeocodingPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("country_code") val countryCode: String? = null,
    val country: String? = null,
    val admin1: String? = null,
)

/** A search hit the user can choose; [label] is what the list shows. */
data class CitySuggestion(
    val name: String,
    val region: String?,
    val country: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val label: String get() = listOfNotNull(name, region, country).distinct().joinToString(", ")
}

/**
 * The city chosen by hand for weather, for people who would rather not share location or
 * whose phone cannot get a fix. While one is set it replaces the device location.
 */
class ManualCityRepository(
    private val api: OpenMeteoGeocodingApi,
    private val store: LocationDataStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _city = MutableStateFlow<LocationSnapshot?>(null)
    val city: StateFlow<LocationSnapshot?> = _city.asStateFlow()

    suspend fun load(): LocationSnapshot? = store.get().also { _city.value = it }

    suspend fun search(query: String): DataResult<List<CitySuggestion>> {
        val name = query.trim()
        if (name.length < 2) return DataResult.Success(emptyList())
        return try {
            DataResult.Success(
                api.search(name).results.map { place ->
                    CitySuggestion(
                        name = place.name,
                        region = place.admin1,
                        country = place.country,
                        countryCode = place.countryCode,
                        latitude = place.latitude,
                        longitude = place.longitude,
                    )
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DataResult.Unavailable("SEARCH_FAILED")
        }
    }

    suspend fun choose(city: CitySuggestion) {
        val snapshot = LocationSnapshot(
            latitude = city.latitude,
            longitude = city.longitude,
            accuracyMeters = null,
            capturedAtEpochMs = clock(),
            district = null,
            city = city.name,
            adminArea = city.region,
            countryCode = city.countryCode,
            displayLabel = city.name,
            source = LocationSource.MANUAL,
        )
        store.save(snapshot)
        _city.value = snapshot
    }

    /** Goes back to the phone's own location. */
    suspend fun clear() {
        store.clear()
        _city.value = null
    }

    companion object {
        private const val BASE_URL = "https://geocoding-api.open-meteo.com/"
        private const val TIMEOUT_SECONDS = 10L

        fun createApi(): OpenMeteoGeocodingApi {
            val json = Json { ignoreUnknownKeys = true }
            val client = OkHttpClient.Builder()
                .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OpenMeteoGeocodingApi::class.java)
        }
    }
}

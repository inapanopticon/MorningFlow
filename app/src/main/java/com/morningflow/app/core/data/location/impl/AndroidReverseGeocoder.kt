package com.morningflow.app.core.data.location.impl

import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Best-effort address enrichment; a failed lookup must not block weather retrieval. */
class AndroidReverseGeocoder(
    context: Context,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val geocoder = Geocoder(context.applicationContext, Locale.getDefault())

    suspend fun resolve(latitude: Double, longitude: Double): ReverseGeocodeResult? =
        withTimeoutOrNull(timeoutMs) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    resolveWithCallback(latitude, longitude)
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)
                            ?.firstOrNull()
                            ?.toResult()
                    }
                }
            }.getOrNull()
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun resolveWithCallback(
        latitude: Double,
        longitude: Double,
    ): ReverseGeocodeResult? = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(
            latitude,
            longitude,
            1,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<android.location.Address>) {
                    if (continuation.isActive) continuation.resume(addresses.firstOrNull()?.toResult())
                }

                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) continuation.resume(null)
                }
            },
        )
    }

    private fun android.location.Address.toResult(): ReverseGeocodeResult =
        ReverseGeocodeResult(
            district = subLocality?.takeIf(String::isNotBlank)
                ?: subAdminArea?.takeIf(String::isNotBlank),
            city = locality?.takeIf(String::isNotBlank)
                ?: subAdminArea?.takeIf(String::isNotBlank),
            adminArea = adminArea?.takeIf(String::isNotBlank),
            countryCode = countryCode?.takeIf(String::isNotBlank),
        )

    companion object {
        const val DEFAULT_TIMEOUT_MS = 3_000L
    }
}

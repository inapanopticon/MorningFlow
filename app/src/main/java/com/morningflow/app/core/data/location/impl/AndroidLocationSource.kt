package com.morningflow.app.core.data.location.impl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.morningflow.app.core.data.location.api.LocationPermissionSpec
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSource
import com.morningflow.app.core.permissions.AndroidPermissionChecker
import com.morningflow.app.core.permissions.PermissionChecker
import java.time.Clock
import java.util.concurrent.Executor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.Channel

/** Gets one foreground reading from Android system providers without GMS. */
class AndroidLocationSource internal constructor(
    context: Context,
    private val permissionChecker: PermissionChecker = AndroidPermissionChecker(context),
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val clock: Clock = Clock.systemUTC(),
    executor: Executor = ContextCompat.getMainExecutor(context),
    private val providerClient: LocationProviderClient = LocationManagerProviderClient(
        context = context,
        executor = executor,
        clock = clock,
    ),
) {
    suspend fun getCurrent(): DataResult<LocationReading> {
        if (!permissionChecker.hasAll(LocationPermissionSpec.requiredPermissions)) {
            return unavailable(LocationFailureReason.PERMISSION_DENIED)
        }

        return try {
            if (!providerClient.isLocationEnabled()) {
                return unavailable(LocationFailureReason.SERVICE_DISABLED)
            }

            val providers = providerClient.availableProviders()
            if (providers.isEmpty()) {
                return unavailable(LocationFailureReason.PROVIDER_UNAVAILABLE)
            }

            val reading = withTimeout(timeoutMs) {
                getFromProviders(providers)
            }
            reading?.let { DataResult.Success(it) }
                ?: unavailable(LocationFailureReason.PROVIDER_UNAVAILABLE)
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            unavailable(LocationFailureReason.TIMEOUT)
        } catch (_: SecurityException) {
            unavailable(LocationFailureReason.PERMISSION_DENIED)
        } catch (_: Exception) {
            unavailable(LocationFailureReason.FAILED)
        }
    }

    /** Queries all available providers within one shared timeout and returns the first result. */
    private suspend fun getFromProviders(providers: List<String>): LocationReading? = coroutineScope {
        val results = Channel<ProviderResult>(capacity = providers.size)
        val jobs = providers.map { provider ->
            launch {
                results.send(ProviderResult(requestProvider(provider)))
            }
        }

        try {
            repeat(providers.size) {
                val result = results.receive().location
                if (result != null) return@coroutineScope result
            }
            null
        } finally {
            jobs.forEach(Job::cancel)
            results.close()
        }
    }

    private data class ProviderResult(val location: LocationReading?)

    private suspend fun requestProvider(provider: String): LocationReading? = try {
        providerClient.getCurrent(provider)
    } catch (error: CancellationException) {
        throw error
    } catch (error: SecurityException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun unavailable(reason: LocationFailureReason): DataResult<Nothing> =
        DataResult.Unavailable(reason.name)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}

/** Small seam that keeps provider orchestration unit-testable without Android framework mocks. */
internal interface LocationProviderClient {
    fun isLocationEnabled(): Boolean

    fun availableProviders(): List<String>

    suspend fun getCurrent(provider: String): LocationReading?
}

/** Production LocationManager implementation used by [AndroidLocationSource]. */
private class LocationManagerProviderClient(
    context: Context,
    private val executor: Executor,
    private val clock: Clock,
) : LocationProviderClient {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun isLocationEnabled(): Boolean =
        LocationManagerCompat.isLocationEnabled(locationManager)

    override fun availableProviders(): List<String> = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
    ).filter { provider ->
        LocationManagerCompat.hasProvider(locationManager, provider) &&
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
    }

    /** The caller checks permission before invoking this provider request. */
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    override suspend fun getCurrent(provider: String): LocationReading? =
        suspendCancellableLocation(provider)

    @SuppressLint("MissingPermission")
    private suspend fun suspendCancellableLocation(provider: String): LocationReading? =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }

            LocationManagerCompat.getCurrentLocation(
                locationManager,
                provider,
                cancellationSignal,
                executor,
            ) { location ->
                if (!continuation.isActive) return@getCurrentLocation
                continuation.resume(
                    location?.let {
                        LocationReading(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            accuracyMeters = it.accuracy.takeUnless { accuracy -> accuracy < 0f },
                            capturedAtEpochMs = it.time.takeIf { timestamp -> timestamp > 0L }
                                ?: clock.millis(),
                            source = when (provider) {
                                LocationManager.GPS_PROVIDER -> LocationSource.GPS
                                LocationManager.NETWORK_PROVIDER -> LocationSource.NETWORK
                                else -> LocationSource.UNKNOWN
                            },
                        )
                    },
                )
            }
        }
}

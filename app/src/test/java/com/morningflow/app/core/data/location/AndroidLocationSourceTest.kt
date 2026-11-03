package com.morningflow.app.core.data.location

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.data.location.impl.AndroidLocationSource
import com.morningflow.app.core.data.location.impl.LocationProviderClient
import com.morningflow.app.core.data.location.impl.LocationReading
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSource
import com.morningflow.app.core.permissions.PermissionChecker
import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidLocationSourceTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val context: Context = application

    @Test
    fun deniedPermissionDoesNotInvokeProviders() = runTest {
        val providers = FakeLocationProviderClient(
            providerResponses = mapOf(
                "network" to completed(null),
                "gps" to completed(null),
            ),
        )

        assertEquals(
            DataResult.Unavailable("PERMISSION_DENIED"),
            source(providers, permissionGranted = false).getCurrent(),
        )
        assertTrue(providers.requestedProviders.isEmpty())
    }

    @Test
    fun securityExceptionFromProviderMapsToPermissionDenied() = runTest {
        val providers = FakeLocationProviderClient(
            providerResponses = mapOf("network" to completed(null)),
            securityExceptionProviders = setOf("network"),
        )

        assertEquals(
            DataResult.Unavailable("PERMISSION_DENIED"),
            source(providers).getCurrent(),
        )
    }

    @Test
    fun gpsResultWinsWhenNetworkProviderDoesNotRespond() = runTest {
        val network = CompletableDeferred<LocationReading?>()
        val gps = CompletableDeferred<LocationReading?>()
        val providers = FakeLocationProviderClient(
            providerResponses = mapOf(
                "network" to network,
                "gps" to gps,
            ),
        )
        val request = async { source(providers).getCurrent() }

        runCurrent()
        assertEquals(setOf("network", "gps"), providers.requestedProviders.toSet())

        gps.complete(reading(LocationSource.GPS))

        assertEquals(DataResult.Success(reading(LocationSource.GPS)), request.await())
        assertTrue("stalled provider should be cancelled", providers.cancelledProviders.contains("network"))
    }

    @Test
    fun gpsIsTriedWhenNetworkReturnsNull() = runTest {
        val providers = FakeLocationProviderClient(
            providerResponses = mapOf(
                "network" to completed(null),
                "gps" to completed(reading(LocationSource.GPS)),
            ),
        )

        assertEquals(
            DataResult.Success(reading(LocationSource.GPS)),
            source(providers).getCurrent(),
        )
    }

    @Test
    fun allProvidersReturningNullBecomeProviderUnavailable() = runTest {
        val providers = FakeLocationProviderClient(
            providerResponses = mapOf(
                "network" to completed(null),
                "gps" to completed(null),
            ),
        )

        assertEquals(
            DataResult.Unavailable("PROVIDER_UNAVAILABLE"),
            source(providers).getCurrent(),
        )
    }

    @Test
    fun providersThatNeverRespondBecomeTimeout() = runTest {
        val providers = FakeLocationProviderClient(
            providerResponses = mapOf(
                "network" to CompletableDeferred(),
                "gps" to CompletableDeferred(),
            ),
        )
        val request = async { source(providers, timeoutMs = 100L).getCurrent() }

        runCurrent()
        advanceTimeBy(100L)

        assertEquals(DataResult.Unavailable("TIMEOUT"), request.await())
        assertTrue(providers.requestedProviders.isNotEmpty())
    }

    private fun source(
        providers: FakeLocationProviderClient,
        timeoutMs: Long = 1_000L,
        permissionGranted: Boolean = true,
    ) = AndroidLocationSource(
        context = context,
        permissionChecker = FakePermissionChecker(permissionGranted),
        timeoutMs = timeoutMs,
        executor = Executor { command -> command.run() },
        providerClient = providers,
    )

    private fun reading(source: LocationSource) = LocationReading(
        latitude = -37.8136,
        longitude = 144.9631,
        accuracyMeters = 20f,
        capturedAtEpochMs = 1_000L,
        source = source,
    )

    private fun completed(value: LocationReading?): CompletableDeferred<LocationReading?> =
        CompletableDeferred<LocationReading?>().also { it.complete(value) }

    private class FakeLocationProviderClient(
        private val providerResponses: Map<String, CompletableDeferred<LocationReading?>>,
        private val securityExceptionProviders: Set<String> = emptySet(),
    ) : LocationProviderClient {
        val requestedProviders = mutableListOf<String>()
        val cancelledProviders = mutableSetOf<String>()

        override fun isLocationEnabled(): Boolean = true

        override fun availableProviders(): List<String> = providerResponses.keys.toList()

        override suspend fun getCurrent(provider: String): LocationReading? {
            requestedProviders += provider
            if (provider in securityExceptionProviders) {
                throw SecurityException("permission revoked while requesting location")
            }
            return try {
                providerResponses.getValue(provider).await()
            } finally {
                if (!currentCoroutineContext().isActive) {
                    cancelledProviders += provider
                }
            }
        }
    }

    private class FakePermissionChecker(
        private val permissionGranted: Boolean,
    ) : PermissionChecker {
        override fun hasAll(permissions: Collection<String>): Boolean = permissionGranted
    }
}

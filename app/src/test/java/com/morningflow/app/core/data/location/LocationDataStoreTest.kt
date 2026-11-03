package com.morningflow.app.core.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.morningflow.app.core.data.location.impl.LocationDataStore
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.location.LocationSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocationDataStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun latestLocationRoundTripsThroughPreferences() = runBlocking {
        val store = LocationDataStore(dataStoreAt(File(temporaryFolder.root, "location.preferences_pb")))
        val expected = LocationSnapshot(
            latitude = -37.8136,
            longitude = 144.9631,
            accuracyMeters = 120f,
            capturedAtEpochMs = 1234L,
            district = "Carlton",
            city = "Melbourne",
            adminArea = "Victoria",
            countryCode = "AU",
            displayLabel = "Carlton, Melbourne",
            source = LocationSource.NETWORK,
        )

        store.save(expected)

        assertEquals(expected, store.get())
    }

    private fun dataStoreAt(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
}

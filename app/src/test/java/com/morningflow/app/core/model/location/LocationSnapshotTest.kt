package com.morningflow.app.core.model.location

import org.junit.Assert.assertThrows
import org.junit.Test

class LocationSnapshotTest {
    @Test
    fun rejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException::class.java) {
            LocationSnapshot(
                latitude = 91.0,
                longitude = 144.0,
                accuracyMeters = null,
                capturedAtEpochMs = 1L,
                district = null,
                city = "Melbourne",
                adminArea = "Victoria",
                countryCode = "AU",
                displayLabel = "Melbourne",
                source = LocationSource.NETWORK,
            )
        }
    }
}

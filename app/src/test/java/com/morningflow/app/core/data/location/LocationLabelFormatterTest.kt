package com.morningflow.app.core.data.location

import com.morningflow.app.core.data.location.impl.LocationLabelFormatter
import com.morningflow.app.core.data.location.impl.ReverseGeocodeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationLabelFormatterTest {
    @Test
    fun prefersDistrictAndCity() {
        assertEquals(
            "Carlton, Melbourne",
            LocationLabelFormatter.format(
                address = ReverseGeocodeResult(
                    district = "Carlton",
                    city = "Melbourne",
                    adminArea = "Victoria",
                    countryCode = "AU",
                ),
                latitude = -37.81,
                longitude = 144.96,
            ),
        )
    }

    @Test
    fun fallsBackToCoordinatesWhenAddressIsUnavailable() {
        assertEquals(
            "-37.8100, 144.9600",
            LocationLabelFormatter.format(null, -37.81, 144.96),
        )
    }

    @Test
    fun avoidsRepeatingTheSameDistrictAndCity() {
        assertEquals(
            "Melbourne",
            LocationLabelFormatter.format(
                address = ReverseGeocodeResult(
                    district = "Melbourne",
                    city = "Melbourne",
                    adminArea = "Victoria",
                    countryCode = "AU",
                ),
                latitude = -37.81,
                longitude = 144.96,
            ),
        )
    }
}

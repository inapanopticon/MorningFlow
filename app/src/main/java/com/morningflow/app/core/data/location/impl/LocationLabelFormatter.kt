package com.morningflow.app.core.data.location.impl

import java.util.Locale

object LocationLabelFormatter {
    fun format(
        address: ReverseGeocodeResult?,
        latitude: Double,
        longitude: Double,
    ): String {
        val district = address?.district?.trim()?.takeIf(String::isNotBlank)
        val city = address?.city?.trim()?.takeIf(String::isNotBlank)
        val adminArea = address?.adminArea?.trim()?.takeIf(String::isNotBlank)
        val country = address?.countryCode?.trim()?.takeIf(String::isNotBlank)

        return when {
            district != null && city != null && !district.equals(city, ignoreCase = true) ->
                "$district, $city"
            city != null -> city
            adminArea != null && country != null -> "$adminArea, $country"
            adminArea != null -> adminArea
            country != null -> country
            else -> String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
        }
    }
}

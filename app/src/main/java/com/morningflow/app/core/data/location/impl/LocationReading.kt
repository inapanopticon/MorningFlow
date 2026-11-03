package com.morningflow.app.core.data.location.impl

import com.morningflow.app.core.model.location.LocationSource

data class LocationReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAtEpochMs: Long,
    val source: LocationSource,
)

package com.morningflow.app.core.model.location

/** A single foreground location reading used as the input to a weather request. */
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAtEpochMs: Long,
    val district: String?,
    val city: String?,
    val adminArea: String?,
    val countryCode: String?,
    val displayLabel: String,
    val source: LocationSource,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "longitude must be between -180 and 180" }
        require(accuracyMeters == null || accuracyMeters >= 0f) {
            "accuracyMeters must be non-negative"
        }
    }
}

enum class LocationSource {
    GPS,
    NETWORK,
    PASSIVE,
    UNKNOWN,

    /** A city the user picked by name instead of sharing location. */
    MANUAL,
}

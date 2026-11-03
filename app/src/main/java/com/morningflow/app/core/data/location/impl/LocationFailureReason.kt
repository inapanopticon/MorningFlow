package com.morningflow.app.core.data.location.impl

/** Failure categories used inside the Android location data source. */
internal enum class LocationFailureReason {
    PERMISSION_DENIED,
    SERVICE_DISABLED,
    PROVIDER_UNAVAILABLE,
    TIMEOUT,
    FAILED,
}

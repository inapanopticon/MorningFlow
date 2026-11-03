package com.morningflow.app.core.data.calendar.impl

/** Data-layer failures mapped to stable reasons before they cross the repository boundary. */
internal enum class CalendarFailureReason {
    PERMISSION_DENIED,
    PROVIDER_UNAVAILABLE,
    QUERY_FAILED,
}

package com.morningflow.app.core.data.notification.api

/** The current Android-side reason a morning prompt can or cannot be posted. */
enum class NotificationAvailability {
    AVAILABLE,
    RUNTIME_PERMISSION_DENIED,
    APP_NOTIFICATIONS_DISABLED,
    CHANNEL_DISABLED,
}

/** Reads current notification availability without requesting permission or opening Settings. */
fun interface NotificationAvailabilityProvider {
    fun current(): NotificationAvailability
}

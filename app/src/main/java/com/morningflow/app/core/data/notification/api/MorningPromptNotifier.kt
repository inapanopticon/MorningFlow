package com.morningflow.app.core.data.notification.api

/** Posts and removes the one-shot wake-up prompt; trigger policy remains outside this API. */
interface MorningPromptNotifier {
    fun post(): NotificationPostResult
    fun cancel()
}

sealed interface NotificationPostResult {
    data object Posted : NotificationPostResult
    data class Unavailable(val reason: NotificationAvailability) : NotificationPostResult
    data object PlatformRejected : NotificationPostResult
}

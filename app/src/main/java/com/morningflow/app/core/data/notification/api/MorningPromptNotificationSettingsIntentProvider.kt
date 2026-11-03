package com.morningflow.app.core.data.notification.api

import android.content.Intent

/** Creates the platform Intent for managing the product notification channel. */
fun interface MorningPromptNotificationSettingsIntentProvider {
    fun createChannelSettingsIntent(): Intent
}

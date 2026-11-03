package com.morningflow.app.core.data.notification.impl

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.morningflow.app.core.data.notification.api.MorningPromptNotificationSettingsIntentProvider

class AndroidMorningPromptNotificationSettingsIntentProvider(
    context: Context,
) : MorningPromptNotificationSettingsIntentProvider {
    private val appContext = context.applicationContext

    override fun createChannelSettingsIntent(): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            .putExtra(
                Settings.EXTRA_CHANNEL_ID,
                MorningPromptNotificationContract.CHANNEL_ID,
            )
}

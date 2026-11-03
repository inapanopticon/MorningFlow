package com.morningflow.app.core.data.notification.impl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.morningflow.app.R

/** Creates the user-controlled product channel without touching the detection-service channel. */
class MorningPromptChannelRegistrar(
    context: Context,
    private val notificationManager: NotificationManager = requireNotNull(
        context.applicationContext.getSystemService(NotificationManager::class.java),
    ),
) {
    private val appContext = context.applicationContext

    fun register() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                MorningPromptNotificationContract.CHANNEL_ID,
                appContext.getString(R.string.morning_prompt_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = appContext.getString(R.string.morning_prompt_channel_description)
            },
        )
    }
}

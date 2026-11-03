package com.morningflow.app.core.data.notification.impl

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.morningflow.app.R
import com.morningflow.app.app.MainActivity
import com.morningflow.app.core.data.notification.api.MorningPromptNotifier
import com.morningflow.app.core.data.notification.api.NotificationAvailability
import com.morningflow.app.core.data.notification.api.NotificationAvailabilityProvider
import com.morningflow.app.core.data.notification.api.NotificationPostResult

class AndroidMorningPromptNotifier(
    context: Context,
    private val availabilityProvider: NotificationAvailabilityProvider =
        AndroidNotificationAvailabilityProvider(context),
    private val channelRegistrar: MorningPromptChannelRegistrar =
        MorningPromptChannelRegistrar(context),
    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context.applicationContext),
) : MorningPromptNotifier {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    override fun post(): NotificationPostResult {
        return try {
            channelRegistrar.register()
            val availability = availabilityProvider.current()
            if (availability != NotificationAvailability.AVAILABLE) {
                return NotificationPostResult.Unavailable(availability)
            }
            notificationManager.notify(
                MorningPromptNotificationContract.NOTIFICATION_ID,
                buildNotification(),
            )
            NotificationPostResult.Posted
        } catch (_: SecurityException) {
            val availability = runCatching { availabilityProvider.current() }.getOrNull()
            if (availability != null && availability != NotificationAvailability.AVAILABLE) {
                NotificationPostResult.Unavailable(availability)
            } else {
                NotificationPostResult.PlatformRejected
            }
        } catch (_: RuntimeException) {
            NotificationPostResult.PlatformRejected
        }
    }

    override fun cancel() {
        notificationManager.cancel(MorningPromptNotificationContract.NOTIFICATION_ID)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(appContext, MorningPromptNotificationContract.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sun)
            .setContentTitle(appContext.getString(R.string.morning_prompt_notification_title))
            .setContentText(appContext.getString(R.string.morning_prompt_notification_body))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        MorningPromptNotificationContract.OPEN_APP_REQUEST_CODE,
        Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

internal object MorningPromptNotificationContract {
    const val CHANNEL_ID = "wake_up_prompt"
    const val NOTIFICATION_ID = 0x5748
    const val OPEN_APP_REQUEST_CODE = 0x5748
}

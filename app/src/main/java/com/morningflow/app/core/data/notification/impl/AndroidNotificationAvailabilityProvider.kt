package com.morningflow.app.core.data.notification.impl

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.morningflow.app.core.data.notification.api.NotificationAvailability
import com.morningflow.app.core.data.notification.api.NotificationAvailabilityProvider
import com.morningflow.app.core.data.notification.api.NotificationPermissionSpec
import com.morningflow.app.core.permissions.AndroidPermissionChecker
import com.morningflow.app.core.permissions.PermissionChecker

class AndroidNotificationAvailabilityProvider(
    context: Context,
    private val permissionChecker: PermissionChecker = AndroidPermissionChecker(context),
    private val apiLevel: Int = Build.VERSION.SDK_INT,
    private val appNotificationsEnabled: () -> Boolean = {
        NotificationManagerCompat.from(context.applicationContext).areNotificationsEnabled()
    },
    private val channelImportance: () -> Int? = {
        context.applicationContext
            .getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(MorningPromptNotificationContract.CHANNEL_ID)
            ?.importance
    },
) : NotificationAvailabilityProvider {

    override fun current(): NotificationAvailability {
        val requiredPermissions = NotificationPermissionSpec.requiredPermissionsFor(apiLevel)
        if (!permissionChecker.hasAll(requiredPermissions)) {
            return NotificationAvailability.RUNTIME_PERMISSION_DENIED
        }
        if (!appNotificationsEnabled()) {
            return NotificationAvailability.APP_NOTIFICATIONS_DISABLED
        }
        if (channelImportance() == NotificationManager.IMPORTANCE_NONE) {
            return NotificationAvailability.CHANNEL_DISABLED
        }
        return NotificationAvailability.AVAILABLE
    }
}

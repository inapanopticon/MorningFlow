package com.morningflow.app.core.data.notification

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.data.notification.api.NotificationAvailability
import com.morningflow.app.core.data.notification.impl.AndroidNotificationAvailabilityProvider
import com.morningflow.app.core.permissions.PermissionChecker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidNotificationAvailabilityProviderTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun runtimePermissionDenialHasPriorityOnAndroid13() {
        val provider = provider(
            permissionGranted = false,
            appEnabled = false,
            channelImportance = NotificationManager.IMPORTANCE_NONE,
        )

        assertEquals(NotificationAvailability.RUNTIME_PERMISSION_DENIED, provider.current())
    }

    @Test
    fun appLevelDisableIsReported() {
        val provider = provider(permissionGranted = true, appEnabled = false)

        assertEquals(NotificationAvailability.APP_NOTIFICATIONS_DISABLED, provider.current())
    }

    @Test
    fun channelDisableIsReported() {
        val provider = provider(
            permissionGranted = true,
            appEnabled = true,
            channelImportance = NotificationManager.IMPORTANCE_NONE,
        )

        assertEquals(NotificationAvailability.CHANNEL_DISABLED, provider.current())
    }

    @Test
    fun aMissingOrEnabledChannelIsAvailable() {
        assertEquals(
            NotificationAvailability.AVAILABLE,
            provider(permissionGranted = true, appEnabled = true, channelImportance = null).current(),
        )
        assertEquals(
            NotificationAvailability.AVAILABLE,
            provider(
                permissionGranted = true,
                appEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_HIGH,
            ).current(),
        )
    }

    private fun provider(
        permissionGranted: Boolean,
        appEnabled: Boolean,
        channelImportance: Int? = NotificationManager.IMPORTANCE_HIGH,
    ) = AndroidNotificationAvailabilityProvider(
        context = application,
        permissionChecker = PermissionChecker { permissionGranted },
        apiLevel = 33,
        appNotificationsEnabled = { appEnabled },
        channelImportance = { channelImportance },
    )
}

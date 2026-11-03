package com.morningflow.app.feature.settings

import android.content.ActivityNotFoundException
import android.os.Build
import android.provider.Settings
import com.morningflow.app.core.data.notification.api.NotificationAvailability
import com.morningflow.app.core.permissions.createExactAlarmSettingsIntent
import com.morningflow.app.core.permissions.launchExactAlarmSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRouteTest {
    @Test
    fun exactAlarmSettingsIntentTargetsMorningFlowSpecialAccess() {
        val intent = requireNotNull(
            createExactAlarmSettingsIntent("com.morningflow.app", Build.VERSION_CODES.S),
        )

        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
        assertEquals("package:com.morningflow.app", intent.dataString)
    }

    @Test
    fun exactAlarmSettingsIsUnavailableBeforeAndroid12() {
        assertNull(
            createExactAlarmSettingsIntent("com.morningflow.app", Build.VERSION_CODES.R),
        )
        assertFalse(launchExactAlarmSettings(null) { error("must not launch") })
    }

    @Test
    fun unavailableExactAlarmSettingsActivityDoesNotCrash() {
        val intent = requireNotNull(
            createExactAlarmSettingsIntent("com.morningflow.app", Build.VERSION_CODES.S),
        )

        assertFalse(
            launchExactAlarmSettings(intent) {
                throw ActivityNotFoundException("missing system handler")
            },
        )
        assertTrue(launchExactAlarmSettings(intent) { })
    }

    @Test
    fun appNotificationSettingsIntentTargetsMorningFlow() {
        val intent = createAppNotificationSettingsIntent("com.morningflow.app")

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals("com.morningflow.app", intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun notificationAvailabilityDispatchesOnlyItsRecoveryAction() {
        var runtimeRequests = 0
        var appSettingsRequests = 0
        var channelSettingsRequests = 0

        fun action(availability: NotificationAvailability) = settingsNotificationRequestAction(
            availability = availability,
            onRuntimePermissionRequest = { runtimeRequests++ },
            onAppNotificationSettings = { appSettingsRequests++ },
            onChannelNotificationSettings = { channelSettingsRequests++ },
        )

        action(NotificationAvailability.AVAILABLE)?.invoke()
        action(NotificationAvailability.RUNTIME_PERMISSION_DENIED)?.invoke()
        action(NotificationAvailability.APP_NOTIFICATIONS_DISABLED)?.invoke()
        action(NotificationAvailability.CHANNEL_DISABLED)?.invoke()

        assertEquals(1, runtimeRequests)
        assertEquals(2, appSettingsRequests)
        assertEquals(1, channelSettingsRequests)
    }
}

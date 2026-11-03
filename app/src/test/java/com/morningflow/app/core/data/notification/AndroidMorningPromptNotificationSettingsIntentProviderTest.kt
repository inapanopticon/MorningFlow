package com.morningflow.app.core.data.notification

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.data.notification.impl.AndroidMorningPromptNotificationSettingsIntentProvider
import com.morningflow.app.core.data.notification.impl.MorningPromptNotificationContract
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidMorningPromptNotificationSettingsIntentProviderTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun createsIntentForTheProductNotificationChannel() {
        val intent = AndroidMorningPromptNotificationSettingsIntentProvider(application)
            .createChannelSettingsIntent()

        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(
            application.packageName,
            intent.getStringExtra(Settings.EXTRA_APP_PACKAGE),
        )
        assertEquals(
            MorningPromptNotificationContract.CHANNEL_ID,
            intent.getStringExtra(Settings.EXTRA_CHANNEL_ID),
        )
    }
}

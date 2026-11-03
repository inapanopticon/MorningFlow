package com.morningflow.app.core.data.notification

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.data.notification.impl.MorningPromptChannelRegistrar
import com.morningflow.app.core.data.notification.impl.MorningPromptNotificationContract
import com.morningflow.app.core.wakeup.WakeDetectionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MorningPromptChannelRegistrarTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = application.getSystemService(NotificationManager::class.java)

    @Test
    fun createsASeparateHighImportanceProductChannelIdempotently() {
        val registrar = MorningPromptChannelRegistrar(application, manager)

        registrar.register()
        registrar.register()

        val channel = manager.getNotificationChannel(MorningPromptNotificationContract.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNotNull(channel.sound)
        assertEquals("Morning prompts", channel.name.toString())
        assertEquals("wake_up_prompt", channel.id)
        assertEquals(1, manager.notificationChannels.count { it.id == channel.id })
        assertNotEquals(WakeDetectionService.CHANNEL_ID, channel.id)
        assertNotEquals(
            WakeDetectionService.NOTIFICATION_ID,
            MorningPromptNotificationContract.NOTIFICATION_ID,
        )
    }
}

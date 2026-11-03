package com.morningflow.app.core.data.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.app.MainActivity
import com.morningflow.app.core.data.notification.api.NotificationAvailability
import com.morningflow.app.core.data.notification.api.NotificationAvailabilityProvider
import com.morningflow.app.core.data.notification.api.NotificationPostResult
import com.morningflow.app.core.data.notification.impl.AndroidMorningPromptNotifier
import com.morningflow.app.core.data.notification.impl.MorningPromptChannelRegistrar
import com.morningflow.app.core.data.notification.impl.MorningPromptNotificationContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidMorningPromptNotifierTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val systemManager = application.getSystemService(NotificationManager::class.java)

    @Before
    fun grantNotificationPermission() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun postsOneDismissiblePersistentPromptThatOpensMainActivity() {
        val notifier = notifier(NotificationAvailability.AVAILABLE)

        assertEquals(NotificationPostResult.Posted, notifier.post())

        val notification = shadowOf(systemManager)
            .getNotification(MorningPromptNotificationContract.NOTIFICATION_ID)
        assertEquals(MorningPromptNotificationContract.CHANNEL_ID, notification.channelId)
        assertEquals("MorningFlow", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "Good morning! Ready to start your morning routine?",
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
        assertEquals(Notification.CATEGORY_REMINDER, notification.category)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertFalse(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(0L, notification.timeoutAfter)
        assertNull(notification.actions)

        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(ComponentName(application, MainActivity::class.java), intent.component)
        assertNull(intent.action)
        assertNull(intent.data)
        assertTrue(intent.extras?.isEmpty ?: true)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun repeatedPostsReplaceTheSameProductCard() {
        val notifier = notifier(NotificationAvailability.AVAILABLE)

        assertEquals(NotificationPostResult.Posted, notifier.post())
        assertEquals(NotificationPostResult.Posted, notifier.post())

        assertEquals(1, systemManager.activeNotifications.size)
        assertEquals(
            MorningPromptNotificationContract.NOTIFICATION_ID,
            systemManager.activeNotifications.single().id,
        )
    }

    @Test
    fun everyUnavailableStatePreventsPosting() {
        NotificationAvailability.entries
            .filter { it != NotificationAvailability.AVAILABLE }
            .forEach { availability ->
                val notifier = notifier(availability)

                assertEquals(
                    NotificationPostResult.Unavailable(availability),
                    notifier.post(),
                )
                assertNull(
                    shadowOf(systemManager)
                        .getNotification(MorningPromptNotificationContract.NOTIFICATION_ID),
                )
            }
    }

    @Test
    fun cancelRemovesTheSameFixedNotification() {
        val notifier = notifier(NotificationAvailability.AVAILABLE)
        notifier.post()

        notifier.cancel()

        assertNull(
            shadowOf(systemManager).getNotification(MorningPromptNotificationContract.NOTIFICATION_ID),
        )
    }

    private fun notifier(availability: NotificationAvailability) = AndroidMorningPromptNotifier(
        context = application,
        availabilityProvider = NotificationAvailabilityProvider { availability },
        channelRegistrar = MorningPromptChannelRegistrar(application, systemManager),
        notificationManager = NotificationManagerCompat.from(application),
    )
}

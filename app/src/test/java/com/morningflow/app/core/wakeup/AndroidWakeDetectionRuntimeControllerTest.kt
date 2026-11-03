package com.morningflow.app.core.wakeup

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Suppress("DEPRECATION")
class AndroidWakeDetectionRuntimeControllerTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun resetAlarms() = ShadowAlarmManager.reset()

    @After
    fun clearAlarms() = ShadowAlarmManager.reset()

    @Test
    fun stopCurrentRunTargetsOnlyTheWakeDetectionService() {
        var stoppedIntent: Intent? = null
        val controller = AndroidWakeDetectionRuntimeController(application) { intent ->
            stoppedIntent = intent
            true
        }

        assertTrue(controller.stopCurrentRun())
        assertEquals(
            WakeDetectionService::class.java.name,
            stoppedIntent?.component?.className,
        )
        assertEquals(null, stoppedIntent?.action)
    }

    @Test
    fun noRunningServiceIsASafeFalseResult() {
        val controller = AndroidWakeDetectionRuntimeController(application) { false }

        assertFalse(controller.stopCurrentRun())
    }

    @Test
    fun platformFailureIsASafeFalseResult() {
        val controller = AndroidWakeDetectionRuntimeController(application) {
            throw SecurityException("test rejection")
        }

        assertFalse(controller.stopCurrentRun())
    }

    @Test
    fun stoppingTheCurrentRunDoesNotRemoveTheFutureAlarm() {
        val alarmManager = requireNotNull(
            application.getSystemService(AlarmManager::class.java),
        )
        val operation = PendingIntent.getBroadcast(
            application,
            91,
            Intent(application, WakeAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.set(AlarmManager.RTC_WAKEUP, 123_456L, operation)
        val controller = AndroidWakeDetectionRuntimeController(application)

        controller.stopCurrentRun()

        val alarms = Shadows.shadowOf(alarmManager).getScheduledAlarms()
        assertEquals(1, alarms.size)
        assertEquals(operation, alarms.single().operation)
    }
}

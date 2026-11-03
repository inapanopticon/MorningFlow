package com.morningflow.app.core.wakeup

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.data.WakeupScheduleFailureReason
import com.morningflow.app.core.data.WakeupScheduleStatus
import com.morningflow.app.core.permissions.ExactAlarmAccessChecker
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.GlobalMorningSchedule
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Suppress("DEPRECATION")
class AndroidWakeupSchedulerTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val alarmManager =
        application.getSystemService(AlarmManager::class.java)
            ?: error("AlarmManager is unavailable in the test environment")

    private val schedule = GlobalMorningSchedule(
        earliestWakeUp = LocalTime.of(7, 0),
        latestWakeUp = LocalTime.of(7, 30),
    )

    @Before
    fun resetAlarms() {
        ShadowAlarmManager.reset()
    }

    @After
    fun clearAlarms() {
        ShadowAlarmManager.reset()
    }

    @Test
    fun scheduleCreatesOneExactRtcWakeupAlarm() = runTest {
        val scheduler = schedulerAt("2026-10-02T06:00:00Z")

        scheduler.schedule(AppSettings(schedule = schedule))

        val alarm = Shadows.shadowOf(alarmManager).getScheduledAlarms().single()
        assertEquals(WakeupScheduleStatus.Scheduled, scheduler.status.value)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(Instant.parse("2026-10-02T07:00:00Z").toEpochMilli(), alarm.triggerAtTime)
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(
            WakeAlarmReceiver::class.java.name,
            Shadows.shadowOf(alarm.operation).savedIntent.component?.className,
        )
    }

    @Test
    fun reschedulingReplacesAlarmAndKeepsTheSamePendingIntentIdentity() = runTest {
        val scheduler = schedulerAt("2026-10-02T06:00:00Z")
        scheduler.schedule(AppSettings(schedule = schedule))
        val first = Shadows.shadowOf(alarmManager).getScheduledAlarms().single()

        val laterSchedule = schedule.copy(
            earliestWakeUp = LocalTime.of(8, 0),
            latestWakeUp = LocalTime.of(8, 30),
        )
        scheduler.schedule(AppSettings(schedule = laterSchedule))

        val alarms = Shadows.shadowOf(alarmManager).getScheduledAlarms()
        assertEquals(1, alarms.size)
        assertEquals(Instant.parse("2026-10-02T08:00:00Z").toEpochMilli(), alarms.single().triggerAtTime)
        assertEquals(first.operation, alarms.single().operation)
    }

    @Test
    fun cancelRemovesTheAlarmAndClearsState() = runTest {
        val scheduler = schedulerAt("2026-10-02T06:00:00Z")
        scheduler.schedule(AppSettings(schedule = schedule))

        scheduler.cancel()

        assertTrue(Shadows.shadowOf(alarmManager).getScheduledAlarms().isEmpty())
        assertEquals(WakeupScheduleStatus.NotScheduled, scheduler.status.value)
    }

    @Test
    fun missingExactAlarmAccessDoesNotArmAndReportsTheReason() = runTest {
        val scheduler = schedulerAt(
            instant = "2026-10-02T06:00:00Z",
            exactAlarmAccess = false,
        )

        scheduler.schedule(AppSettings(schedule = schedule))

        assertTrue(Shadows.shadowOf(alarmManager).getScheduledAlarms().isEmpty())
        assertEquals(
            WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.EXACT_ACCESS_REQUIRED),
            scheduler.status.value,
        )
    }

    @Test
    fun invalidScheduleDoesNotArmAndReportsTheReason() = runTest {
        val scheduler = schedulerAt("2026-10-02T06:00:00Z")
        val invalid = schedule.copy(latestWakeUp = schedule.earliestWakeUp)

        scheduler.schedule(AppSettings(schedule = invalid))

        assertTrue(Shadows.shadowOf(alarmManager).getScheduledAlarms().isEmpty())
        assertEquals(
            WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.ZERO_LENGTH_WINDOW),
            scheduler.status.value,
        )
    }

    @Test
    fun invalidReplacementCancelsThePreviouslyScheduledAlarm() = runTest {
        val scheduler = schedulerAt("2026-10-02T06:00:00Z")
        scheduler.schedule(AppSettings(schedule = schedule))

        scheduler.schedule(
            AppSettings(schedule = schedule.copy(latestWakeUp = schedule.earliestWakeUp)),
        )

        assertTrue(Shadows.shadowOf(alarmManager).getScheduledAlarms().isEmpty())
        assertEquals(
            WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.ZERO_LENGTH_WINDOW),
            scheduler.status.value,
        )
    }

    @Test
    fun reversedScheduleReportsItsSpecificReason() = runTest {
        val scheduler = schedulerAt("2026-10-02T06:00:00Z")

        scheduler.schedule(
            AppSettings(schedule = schedule.copy(latestWakeUp = LocalTime.of(6, 30))),
        )

        assertEquals(
            WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.END_BEFORE_START),
            scheduler.status.value,
        )
    }

    @Test
    fun platformRejectionIsReportedWithoutLeavingAnAlarm() = runTest {
        val scheduler = schedulerAt(
            instant = "2026-10-02T06:00:00Z",
            exactAlarmSetter = { _, _ -> error("platform rejected request") },
        )

        scheduler.schedule(AppSettings(schedule = schedule))

        assertTrue(Shadows.shadowOf(alarmManager).getScheduledAlarms().isEmpty())
        assertEquals(
            WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.PLATFORM_REJECTED),
            scheduler.status.value,
        )
    }

    private fun schedulerAt(
        instant: String,
        exactAlarmAccess: Boolean = true,
        exactAlarmSetter: ((Long, android.app.PendingIntent) -> Unit)? = null,
    ): AndroidWakeupScheduler {
        val zone = ZoneOffset.UTC
        return AndroidWakeupScheduler(
            context = application,
            alarmManager = alarmManager,
            calculator = WakeScheduleCalculator(
                clock = Clock.fixed(Instant.parse(instant), zone),
                zoneId = zone,
            ),
            exactAlarmAccessChecker = ExactAlarmAccessChecker { exactAlarmAccess },
            exactAlarmSetter = exactAlarmSetter ?: { triggerAtMillis, operation ->
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation,
                )
            },
        )
    }
}

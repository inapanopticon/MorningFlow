package com.morningflow.app.core.wakeup

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.model.GlobalMorningSchedule
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
@Suppress("DEPRECATION")
class WakeAlarmReceiverTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val shadowApplication = Shadows.shadowOf(application)
    private var nextAlarmRequests = 0

    @Before
    fun setUp() {
        nextAlarmRequests = 0
        shadowApplication.clearStartedServices()
    }

    @Test
    fun validAlarmRequestsSerializedReconcileAndStartsDetectionService() {
        val schedule = activeSchedule()
        val intent = WakeAlarmContract.startIntent(application, schedule)

        receiver().onReceive(application, intent)

        assertEquals(1, nextAlarmRequests)
        val serviceIntent = shadowApplication.nextStartedService
        assertEquals(WakeDetectionService::class.java.name, serviceIntent.component?.className)
        assertEquals(WakeDetectionService.ACTION_RUN_DETECTION, serviceIntent.action)
        assertEquals(
            WakeScheduleSnapshot.from(schedule),
            WakeAlarmContract.readSnapshot(serviceIntent),
        )
    }

    @Test
    fun expiredAlarmStillRequestsReconcileButDoesNotStartService() {
        val schedule = inactiveSchedule()

        receiver().onReceive(
            application,
            WakeAlarmContract.startIntent(application, schedule),
        )

        assertNull(shadowApplication.nextStartedService)
        assertEquals(1, nextAlarmRequests)
    }

    @Test
    fun malformedOrWrongActionIsIgnored() {
        receiver().onReceive(
            application,
            Intent(application, WakeAlarmReceiver::class.java)
                .setAction(WakeAlarmContract.ACTION_START)
                .putExtra(WakeAlarmContract.EXTRA_EARLIEST, "not-a-time")
                .putExtra(WakeAlarmContract.EXTRA_LATEST, "07:30"),
        )
        receiver().onReceive(
            application,
            WakeAlarmContract.startIntent(application, activeSchedule()).setAction("wrong"),
        )

        assertNull(shadowApplication.nextStartedService)
        assertEquals(0, nextAlarmRequests)
    }

    @Test
    fun zeroLengthSnapshotIsIgnored() {
        val same = LocalTime.of(7, 0)
        val intent = Intent(application, WakeAlarmReceiver::class.java)
            .setAction(WakeAlarmContract.ACTION_START)
            .putExtra(WakeAlarmContract.EXTRA_EARLIEST, same.toString())
            .putExtra(WakeAlarmContract.EXTRA_LATEST, same.toString())

        receiver().onReceive(application, intent)

        assertNull(shadowApplication.nextStartedService)
        assertEquals(0, nextAlarmRequests)
    }

    @Test
    fun androidCanConstructTheProductionReceiver() {
        assertNotNull(WakeAlarmReceiver::class.java.getDeclaredConstructor().newInstance())
    }

    private fun receiver(): WakeAlarmReceiver = WakeAlarmReceiver {
        nextAlarmRequests += 1
        Job().apply { complete() }
    }

    private fun activeSchedule(): GlobalMorningSchedule {
        val now = LocalTime.now(ZoneId.systemDefault())
        return if (now.isBefore(LocalTime.NOON)) {
            GlobalMorningSchedule(LocalTime.MIDNIGHT, LocalTime.NOON)
        } else {
            GlobalMorningSchedule(LocalTime.NOON, LocalTime.MAX)
        }
    }

    private fun inactiveSchedule(): GlobalMorningSchedule {
        val now = LocalTime.now(ZoneId.systemDefault())
        return if (now.isBefore(LocalTime.NOON)) {
            GlobalMorningSchedule(LocalTime.NOON, LocalTime.of(13, 0))
        } else {
            GlobalMorningSchedule(LocalTime.MIDNIGHT, LocalTime.of(1, 0))
        }
    }
}

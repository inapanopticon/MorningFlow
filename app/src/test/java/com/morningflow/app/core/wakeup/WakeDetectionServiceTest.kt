package com.morningflow.app.core.wakeup

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.app.MorningFlowApplication
import com.morningflow.app.core.data.FakeWakeUpDetection
import com.morningflow.app.core.data.RealWakeUpDetection
import com.morningflow.app.core.data.WakeUpDetection
import com.morningflow.app.core.data.notification.api.MorningPromptNotifier
import com.morningflow.app.core.data.notification.api.NotificationAvailability
import com.morningflow.app.core.data.notification.api.NotificationPostResult
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WakeDetectionServiceTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val now = Instant.parse("2026-10-03T07:10:00Z")
    private val snapshot = WakeScheduleSnapshot(
        earliest = LocalTime.of(7, 0),
        latest = LocalTime.of(7, 30),
    )

    @Test
    fun startResetsDetectionAndCreatesForegroundNotification() = runTest {
        val detection = FakeWakeUpDetection(clock = Clock.fixed(now, ZoneOffset.UTC)).apply {
            start()
            simulateWakeUp()
            stop()
        }
        val controller = startService(detection, StandardTestDispatcher(testScheduler))

        runCurrent()

        assertTrue(detection.isRunning)
        assertEquals(null, detection.state.value.detectedAt)
        val shadowService = Shadows.shadowOf(controller.get())
        assertEquals(WakeDetectionService.NOTIFICATION_ID, shadowService.lastForegroundNotificationId)
        assertNotNull(shadowService.lastForegroundNotification)
        val manager = application.getSystemService(NotificationManager::class.java)
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            manager?.getNotificationChannel(WakeDetectionService.CHANNEL_ID)?.importance,
        )
        controller.destroy()
    }

    @Test
    fun detectionPostsOnceAndStopsTheRuntime() = runTest {
        val detection = FakeWakeUpDetection(clock = Clock.fixed(now, ZoneOffset.UTC))
        val notifier = RecordingMorningPromptNotifier()
        val controller = startService(
            detection,
            StandardTestDispatcher(testScheduler),
            notifier,
        )
        runCurrent()

        detection.simulateWakeUp()
        runCurrent()
        detection.simulateWakeUp()
        runCurrent()

        assertFalse(detection.isRunning)
        assertEquals(1, notifier.postCalls)
        assertTrue(Shadows.shadowOf(controller.get()).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun unavailableAndRejectedPromptsStopWithoutRetry() = runTest {
        val results = listOf(
            NotificationPostResult.Unavailable(NotificationAvailability.RUNTIME_PERMISSION_DENIED),
            NotificationPostResult.PlatformRejected,
        )

        results.forEachIndexed { index, result ->
            val detection = FakeWakeUpDetection(clock = Clock.fixed(now, ZoneOffset.UTC))
            val notifier = RecordingMorningPromptNotifier(result)
            val controller = startService(
                detection,
                StandardTestDispatcher(testScheduler),
                notifier,
                startId = index + 1,
            )
            runCurrent()

            detection.simulateWakeUp()
            runCurrent()
            advanceTimeBy(20.minutes)
            runCurrent()

            assertEquals(1, notifier.postCalls)
            assertFalse(detection.isRunning)
            assertTrue(Shadows.shadowOf(controller.get()).isStoppedBySelf)
            controller.destroy()
        }
    }

    @Test
    fun notifierExceptionStillStopsDetection() = runTest {
        val detection = FakeWakeUpDetection(clock = Clock.fixed(now, ZoneOffset.UTC))
        val notifier = RecordingMorningPromptNotifier(throwOnPost = true)
        val controller = startService(
            detection,
            StandardTestDispatcher(testScheduler),
            notifier,
        )
        runCurrent()

        detection.simulateWakeUp()
        runCurrent()

        assertEquals(1, notifier.postCalls)
        assertFalse(detection.isRunning)
        assertTrue(Shadows.shadowOf(controller.get()).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun rebuiltServiceAllowsOnePromptForEachRun() = runTest {
        repeat(2) { index ->
            val detection = FakeWakeUpDetection(clock = Clock.fixed(now, ZoneOffset.UTC))
            val notifier = RecordingMorningPromptNotifier()
            val controller = startService(
                detection,
                StandardTestDispatcher(testScheduler),
                notifier,
                startId = index + 1,
            )
            runCurrent()

            detection.simulateWakeUp()
            runCurrent()

            assertEquals(1, notifier.postCalls)
            controller.destroy()
        }
    }

    @Test
    fun replacementCommandKeepsOneActiveCollectorAndOnePromptRequest() = runTest {
        val detection = FakeWakeUpDetection(clock = Clock.fixed(now, ZoneOffset.UTC))
        val notifier = RecordingMorningPromptNotifier()
        val controller = startService(
            detection,
            StandardTestDispatcher(testScheduler),
            notifier,
        )
        runCurrent()

        controller.startCommand(0, 2)
        runCurrent()
        detection.simulateWakeUp()
        runCurrent()

        assertEquals(1, notifier.postCalls)
        assertFalse(detection.isRunning)
        assertTrue(Shadows.shadowOf(controller.get()).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun latestDeadlineStopsDetection() = runTest {
        val detection = FakeWakeUpDetection(clock = Clock.fixed(now, ZoneOffset.UTC))
        val controller = startService(detection, StandardTestDispatcher(testScheduler))
        runCurrent()

        advanceTimeBy(20.minutes)
        runCurrent()

        assertFalse(detection.isRunning)
        assertEquals(0, WakeDetectionServiceHarness.notifier.postCalls)
        assertTrue(Shadows.shadowOf(controller.get()).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun destroyAlwaysStopsDetection() = runTest {
        val detection = FakeWakeUpDetection(clock = Clock.fixed(now, ZoneOffset.UTC))
        val controller = startService(detection, StandardTestDispatcher(testScheduler))
        runCurrent()
        assertTrue(detection.isRunning)

        controller.destroy()

        assertFalse(detection.isRunning)
    }

    @Test
    fun stoppedRunCannotPostAfterLaterDetectionSignal() = runTest {
        val detection = FakeWakeUpDetection(clock = Clock.fixed(now, ZoneOffset.UTC))
        val notifier = RecordingMorningPromptNotifier()
        val controller = startService(
            detection,
            StandardTestDispatcher(testScheduler),
            notifier,
        )
        runCurrent()

        controller.destroy()
        detection.simulateWakeUp()
        runCurrent()

        assertFalse(detection.isRunning)
        assertEquals(0, notifier.postCalls)
    }

    /**
     * The detector reads non-wakeup sensors, whose events stop once the SoC suspends
     * with the screen off. The Service therefore holds a partial wake lock for the
     * bounded detection window - and must let go of it the moment the run ends.
     */
    @Test
    fun wakeLockIsHeldWhileDetectingAndReleasedWhenDetectionEnds() = runTest {
        val detection = FakeWakeUpDetection(clock = Clock.fixed(now, ZoneOffset.UTC))
        val controller = startService(detection, StandardTestDispatcher(testScheduler))
        runCurrent()

        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        assertNotNull(wakeLock)
        assertTrue(wakeLock.isHeld)

        detection.simulateWakeUp()
        runCurrent()

        assertFalse(wakeLock.isHeld)
        controller.destroy()
    }

    @Test
    fun destroyReleasesTheWakeLock() = runTest {
        val detection = FakeWakeUpDetection(clock = Clock.fixed(now, ZoneOffset.UTC))
        val controller = startService(detection, StandardTestDispatcher(testScheduler))
        runCurrent()
        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        assertTrue(wakeLock.isHeld)

        controller.destroy()

        assertFalse(wakeLock.isHeld)
    }

    @Test
    fun unsupportedDetectionExitsSafely() = runTest {
        val detection = FakeWakeUpDetection(
            supported = false,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val controller = startService(detection, StandardTestDispatcher(testScheduler))
        runCurrent()

        assertFalse(detection.isRunning)
        assertTrue(Shadows.shadowOf(controller.get()).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun productionBindingUsesTheApplicationContainerDetection() {
        val morningFlowApplication =
            ApplicationProvider.getApplicationContext<MorningFlowApplication>()
        val controller = Robolectric.buildService(ProductionBindingProbeService::class.java)
            .create()

        val resolved = controller.get().resolvedDetection()
        val resolvedNotifier = controller.get().resolvedNotifier()

        assertSame(morningFlowApplication.appContainer.wakeUpDetection, resolved)
        assertSame(morningFlowApplication.appContainer.morningPromptNotifier, resolvedNotifier)
        assertTrue(resolved is RealWakeUpDetection)
        controller.destroy()
    }

    private fun startService(
        detection: WakeUpDetection,
        dispatcher: TestDispatcher,
        notifier: RecordingMorningPromptNotifier = RecordingMorningPromptNotifier(),
        startId: Int = 1,
    ): ServiceController<TestWakeDetectionService> {
        WakeDetectionServiceHarness.detection = detection
        WakeDetectionServiceHarness.notifier = notifier
        WakeDetectionServiceHarness.dispatcher = dispatcher
        WakeDetectionServiceHarness.clock = Clock.fixed(now, ZoneOffset.UTC)
        val intent = WakeDetectionService.startIntent(application, snapshot)
        return Robolectric.buildService(TestWakeDetectionService::class.java, intent)
            .create()
            .startCommand(0, startId)
    }
}

internal object WakeDetectionServiceHarness {
    lateinit var detection: WakeUpDetection
    lateinit var notifier: RecordingMorningPromptNotifier
    lateinit var dispatcher: CoroutineDispatcher
    lateinit var clock: Clock
}

internal class TestWakeDetectionService : WakeDetectionService() {
    override fun createWakeUpDetection(): WakeUpDetection = WakeDetectionServiceHarness.detection

    override fun createMorningPromptNotifier(): MorningPromptNotifier =
        WakeDetectionServiceHarness.notifier

    override fun serviceClock(): Clock = WakeDetectionServiceHarness.clock

    override fun serviceDispatcher(): CoroutineDispatcher = WakeDetectionServiceHarness.dispatcher
}

internal class ProductionBindingProbeService : WakeDetectionService() {
    fun resolvedDetection(): WakeUpDetection? = createWakeUpDetection()
    fun resolvedNotifier(): MorningPromptNotifier? = createMorningPromptNotifier()
}

internal class RecordingMorningPromptNotifier(
    private val result: NotificationPostResult = NotificationPostResult.Posted,
    private val throwOnPost: Boolean = false,
) : MorningPromptNotifier {
    var postCalls = 0
        private set

    override fun post(): NotificationPostResult {
        postCalls += 1
        if (throwOnPost) throw IllegalStateException("test prompt failure")
        return result
    }

    override fun cancel() = Unit
}

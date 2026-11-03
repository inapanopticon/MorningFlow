package com.morningflow.app.core.data

import com.morningflow.app.core.model.BehaviourKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorBehaviourDetectionTest {

    /** Stands in for a real detector: the test sets the count and availability. */
    private class FakeSensor(var available: Boolean = true) : CountingSensor {
        val counts = MutableStateFlow(0)
        override val count: StateFlow<Int> = counts
        override val isAvailable: Boolean get() = available
        var running = false
            private set
        val calls = mutableListOf<String>()

        override fun start() {
            calls += "start"
            running = available
        }

        override fun pause() {
            calls += "pause"
            running = false
        }

        override fun stop() {
            calls += "stop"
            running = false
        }

        override fun reset() {
            calls += "reset"
            running = false
            counts.value = 0
        }

        /** A confirmed count from the hardware. Only arrives while running, like the real detectors. */
        fun count(value: Int) {
            if (running) counts.value = value
        }
    }

    private data class Report(val token: Long, val count: Int, val available: Boolean, val failed: Boolean)

    private val squats = FakeSensor()
    private val steps = FakeSensor()
    private val reports = mutableListOf<Report>()

    // Dispatchers.Unconfined collects synchronously, so reports arrive before the next line runs.
    private val detection = SensorBehaviourDetection(squats, steps, CoroutineScope(Dispatchers.Unconfined)).apply {
        report = { token, count, available, failed -> reports += Report(token, count, available, failed) }
    }

    @Test
    fun beginSquatsResetsThenStartsTheSquatSensorAndReportsZeroAtOnce() {
        squats.counts.value = 7 // left over from an earlier session

        detection.begin(BehaviourKey.SQUATS, token = 1)

        assertEquals(listOf("reset", "start"), squats.calls)
        assertEquals(listOf(Report(1, 0, true, false)), reports)
        assertTrue(steps.calls.isEmpty())
    }

    @Test
    fun everyCountChangeIsReportedWithTheBeginToken() {
        detection.begin(BehaviourKey.SQUATS, token = 4)
        squats.count(1)
        squats.count(2)

        assertEquals(listOf(0, 1, 2), reports.map { it.count })
        assertTrue(reports.all { it.token == 4L })
    }

    @Test
    fun walkingUsesTheStepSensor() {
        detection.begin(BehaviourKey.WALKING, token = 2)
        steps.count(12)

        assertEquals(listOf(Report(2, 0, true, false), Report(2, 12, true, false)), reports)
        assertTrue(squats.calls.isEmpty())
    }

    @Test
    fun behavioursWithoutASensorAreIgnored() {
        detection.begin(BehaviourKey.STRETCHING, token = 3)

        assertTrue(reports.isEmpty())
        assertTrue(squats.calls.isEmpty())
        assertTrue(steps.calls.isEmpty())
    }

    @Test
    fun beginningTheNextBehaviourStopsThePreviousSensor() {
        detection.begin(BehaviourKey.SQUATS, token = 1)
        detection.begin(BehaviourKey.WALKING, token = 2)
        squats.counts.value = 9 // a late squat after the switch

        assertEquals("stop", squats.calls.last())
        assertTrue(reports.none { it.token == 1L && it.count == 9 })
    }

    @Test
    fun pauseKeepsTheCountAndReportsNothingUntilResume() {
        detection.begin(BehaviourKey.SQUATS, token = 5)
        squats.count(3)

        detection.pause()
        squats.count(4) // ignored: the sensor is paused
        assertEquals("pause", squats.calls.last())
        assertEquals(3, reports.last().count)

        detection.resume()
        assertEquals("start", squats.calls.last())
        assertFalse(squats.calls.takeLast(2).contains("reset"))
        assertEquals(Report(5, 3, true, false), reports.last())

        squats.count(4)
        assertEquals(Report(5, 4, true, false), reports.last())
    }

    @Test
    fun anUnavailableSensorIsReportedAsUnavailable() {
        squats.available = false

        detection.begin(BehaviourKey.SQUATS, token = 6)

        assertEquals(listOf(Report(6, 0, false, false)), reports)
    }

    @Test
    fun aPermissionLostBeforeResumeIsReportedAsUnavailable() {
        detection.begin(BehaviourKey.WALKING, token = 7)
        steps.count(20)
        detection.pause()

        steps.available = false
        detection.resume()

        assertEquals(Report(7, 20, false, false), reports.last())
    }

    @Test
    fun stopStopsTheSensorAndDropsLateCounts() {
        detection.begin(BehaviourKey.SQUATS, token = 8)
        detection.stop()
        val before = reports.size

        squats.counts.value = 5 // a straggler after stop
        detection.resume()      // nothing to resume once stopped

        assertEquals("stop", squats.calls.last())
        assertEquals(before, reports.size)
    }

    @Test
    fun recognitionFailedIsNeverReported() {
        detection.begin(BehaviourKey.SQUATS, token = 9)
        squats.count(1)

        assertTrue(reports.none { it.failed })
    }

    @Test
    fun worksBeforeReportIsConnected() {
        val unwired = SensorBehaviourDetection(FakeSensor(), FakeSensor(), CoroutineScope(Dispatchers.Unconfined))

        unwired.begin(BehaviourKey.SQUATS, token = 1) // must not crash
        unwired.stop()
    }
}

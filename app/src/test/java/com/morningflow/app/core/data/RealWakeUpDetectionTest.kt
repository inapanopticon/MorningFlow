package com.morningflow.app.core.data

import com.morningflow.app.core.sensor.WakeUpSignalSource
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealWakeUpDetectionTest {

    /** Stands in for WakeUpDetector: the test decides when a raw detection fires. */
    private class FakeSource(
        override val isSupported: Boolean = true,
        private val registrationSucceeds: Boolean = true,
    ) : WakeUpSignalSource {
        override var onWakeUp: (() -> Unit)? = null
        var listening = false
            private set
        var startCalls = 0
            private set

        override fun start(): Boolean {
            startCalls++
            listening = isSupported && registrationSucceeds
            return listening
        }

        override fun stop() {
            listening = false
        }

        override fun reset() {
            listening = false
        }

        /** A raw detection, delivered even when not listening, like a late sensor event. */
        fun fire() {
            onWakeUp?.invoke()
        }
    }

    private val t0 = Instant.parse("2026-10-01T06:45:00Z")
    private val clock = MutableClock(t0)
    private val source = FakeSource()
    private val detection = RealWakeUpDetection(source, WakeUpDetectionGate(clock = clock))

    @Test
    fun doesNotStartOnItsOwn() {
        assertFalse(source.listening)
        assertEquals(0, source.startCalls)
        assertEquals(WakeUpState(available = true, detectedAt = null), detection.state.value)
    }

    @Test
    fun signalsBeforeStartAreIgnored() {
        source.fire()

        assertNull(detection.state.value.detectedAt)
    }

    @Test
    fun aDetectionWhileRunningIsStampedWithTheCurrentTime() {
        assertTrue(detection.start())
        source.fire()

        assertEquals(t0, detection.state.value.detectedAt)
    }

    @Test
    fun repeatsWithinTwoMinutesKeepTheFirstTimestamp() {
        detection.start()
        source.fire()

        clock.advance(Duration.ofSeconds(90))
        source.fire()

        assertEquals(t0, detection.state.value.detectedAt)
    }

    @Test
    fun aDetectionAfterTwoMinutesIsANewWakeUp() {
        detection.start()
        source.fire()

        clock.advance(Duration.ofMinutes(2))
        source.fire()

        assertEquals(t0.plus(Duration.ofMinutes(2)), detection.state.value.detectedAt)
    }

    @Test
    fun stopDiscardsLateSignalsButKeepsTheLastDetection() {
        detection.start()
        source.fire()

        detection.stop()
        clock.advance(Duration.ofMinutes(5))
        source.fire()

        assertFalse(source.listening)
        assertEquals(t0, detection.state.value.detectedAt)
    }

    @Test
    fun resetForgetsTheDetectionAndKeepsRunning() {
        detection.start()
        source.fire()

        clock.advance(Duration.ofSeconds(30))
        detection.reset()
        assertNull(detection.state.value.detectedAt)
        assertTrue(source.listening)

        // The two-minute gate was cleared too, so this counts at once.
        source.fire()
        assertEquals(t0.plus(Duration.ofSeconds(30)), detection.state.value.detectedAt)
    }

    @Test
    fun resetWhileStoppedStaysStopped() {
        detection.reset()

        assertFalse(source.listening)
        assertEquals(0, source.startCalls)
    }

    @Test
    fun startTwiceRegistersOnce() {
        detection.start()
        detection.start()

        assertEquals(1, source.startCalls)
    }

    @Test
    fun aDeviceWithoutTheSensorsReportsUnavailable() {
        val unsupported = RealWakeUpDetection(FakeSource(isSupported = false))

        assertFalse(unsupported.state.value.available)
        assertFalse(unsupported.start())
        assertFalse(unsupported.state.value.available)
    }

    @Test
    fun aFailedRegistrationReportsUnavailable() {
        val failing = RealWakeUpDetection(FakeSource(registrationSucceeds = false))

        assertTrue(failing.state.value.available)
        assertFalse(failing.start())
        assertFalse(failing.state.value.available)
    }
}

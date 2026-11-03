package com.morningflow.app.core.data

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FakeWakeUpDetectionTest {
    private val t0 = Instant.parse("2026-10-01T06:45:00Z")
    private val clock = MutableClock(t0)
    private val detection = FakeWakeUpDetection(clock = clock)

    @Test
    fun simulatedWakeUpsCountOnlyWhileRunning() {
        detection.simulateWakeUp()
        assertNull(detection.state.value.detectedAt)

        detection.start()
        detection.simulateWakeUp()
        assertEquals(t0, detection.state.value.detectedAt)
    }

    @Test
    fun followsTheSameTwoMinuteRuleAsTheRealSensor() {
        detection.start()
        detection.simulateWakeUp()

        clock.advance(Duration.ofMinutes(1))
        detection.simulateWakeUp()
        assertEquals(t0, detection.state.value.detectedAt)

        clock.advance(Duration.ofMinutes(1))
        detection.simulateWakeUp()
        assertEquals(t0.plus(Duration.ofMinutes(2)), detection.state.value.detectedAt)
    }

    @Test
    fun anUnsupportedDeviceNeverStarts() {
        val unsupported = FakeWakeUpDetection(supported = false, clock = clock)

        assertFalse(unsupported.start())
        unsupported.simulateWakeUp()
        assertFalse(unsupported.state.value.available)
        assertNull(unsupported.state.value.detectedAt)
    }
}

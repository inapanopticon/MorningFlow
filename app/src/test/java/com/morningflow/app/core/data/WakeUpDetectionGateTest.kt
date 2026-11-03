package com.morningflow.app.core.data

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WakeUpDetectionGateTest {
    private val start = Instant.parse("2026-09-10T07:05:00Z")
    private val clock = MutableClock(start)
    private val gate = WakeUpDetectionGate(Duration.ofMinutes(2), clock)

    @Test
    fun theFirstSignalIsAlwaysANewDetection() {
        assertEquals(start, gate.offer())
        assertEquals(start, gate.lastAcceptedAt)
    }

    @Test
    fun signalsInsideTheIntervalAreTheSameDetectionStillOngoing() {
        gate.offer()

        clock.advance(Duration.ofSeconds(1))
        assertNull(gate.offer())
        clock.advance(Duration.ofSeconds(90))
        assertNull(gate.offer())

        // The published timestamp never moved, so the snapshot stays byte-identical and
        // no spurious reminder can reach the UI.
        assertEquals(start, gate.lastAcceptedAt)
    }

    @Test
    fun aSignalAfterTheIntervalIsANewDetectionWithADistinctTimestamp() {
        val first = gate.offer()

        clock.advance(Duration.ofMinutes(2))
        val second = gate.offer()

        assertNotNull(second)
        assertEquals(start.plus(Duration.ofMinutes(2)), second)
        // Distinctness is the whole contract: this is what makes the repeat reminder fire.
        assertEquals(false, first == second)
    }

    @Test
    fun everyIntervalYieldsExactlyOneDetection() {
        val accepted = mutableListOf<Instant>()
        // Sample once a second for six minutes, as a real algorithm would.
        repeat(6 * 60) {
            gate.offer()?.let(accepted::add)
            clock.advance(Duration.ofSeconds(1))
        }

        // t=0, t=2min, t=4min: a two-minute floor over six minutes of continuous signal.
        assertEquals(3, accepted.size)
        assertEquals(accepted.distinct(), accepted)
    }

    @Test
    fun resetStartsAFreshSequence() {
        gate.offer()
        clock.advance(Duration.ofSeconds(1))
        assertNull(gate.offer())

        gate.reset()

        assertNull(gate.lastAcceptedAt)
        assertNotNull(gate.offer())
    }
}

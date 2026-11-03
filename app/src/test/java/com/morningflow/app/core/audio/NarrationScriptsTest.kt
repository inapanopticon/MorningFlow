package com.morningflow.app.core.audio

import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the shape of the generated narration: every timed behaviour has all three
 * lengths in the recorded voice, longer lengths only add lines, and sensor steps stay
 * quiet once they have started. The timing itself is checked against the real
 * recordings by NarrationPacingTest on a device.
 */
class NarrationScriptsTest {
    private val lengths = listOf(BehaviourMode.SHORT, BehaviourMode.MEDIUM, BehaviourMode.LONG)
    private val timed = BehaviourKey.entries.filter { !it.isSensor && it != BehaviourKey.LEAVING_REMINDER }

    @Test
    fun everyBehaviourHasAScript() {
        BehaviourKey.entries.forEach { key ->
            assertTrue("$key has no narration", NarrationScripts[key].isNotEmpty())
        }
    }

    @Test
    fun everyTimedLengthIsRecorded() {
        timed.forEach { key ->
            lengths.forEach { mode ->
                NarrationScripts[key, mode].forEachIndexed { index, segment ->
                    assertTrue("$key $mode line $index has no recording", segment.clipRes != NarrationSegment.NO_CLIP)
                    assertTrue("$key $mode line $index has no text", segment.text.isNotBlank())
                    assertTrue("$key $mode line $index has a negative pause", segment.pauseAfterMillis >= 0)
                }
            }
        }
    }

    /** Longer versions add company; they never drop what the shorter one says. */
    @Test
    fun longerLengthsKeepEveryLineOfTheShorterOnes() {
        timed.forEach { key ->
            val short = NarrationScripts[key, BehaviourMode.SHORT].map { it.clipRes }
            val medium = NarrationScripts[key, BehaviourMode.MEDIUM].map { it.clipRes }
            val long = NarrationScripts[key, BehaviourMode.LONG].map { it.clipRes }
            assertTrue("$key Medium drops a Short line", medium.containsAll(short))
            assertTrue("$key Long drops a Medium line", long.containsAll(medium))
            assertTrue("$key Long adds nothing over Short", long.size > short.size)
        }
    }

    /** All three lengths open and close on the same recordings, so they sound like one voice. */
    @Test
    fun allLengthsShareTheirOpeningAndClosingLines() {
        timed.forEach { key ->
            val versions = lengths.map { NarrationScripts[key, it] }
            assertEquals("$key", 1, versions.map { it.first().clipRes }.toSet().size)
            assertEquals("$key", 1, versions.map { it.last().clipRes }.toSet().size)
        }
    }

    /** Every version ends on its closing line, with the headroom left as silence after it. */
    @Test
    fun theClosingLineHasNoPauseOfItsOwn() {
        timed.forEach { key ->
            lengths.forEach { mode -> assertEquals("$key $mode", 0L, NarrationScripts[key, mode].last().pauseAfterMillis) }
        }
    }

    @Test
    fun behavioursThatCannotBeTurnedOffStillDefaultToMedium() {
        assertEquals(NarrationScripts[BehaviourKey.BRUSH_TEETH, BehaviourMode.MEDIUM], NarrationScripts[BehaviourKey.BRUSH_TEETH])
        assertEquals(NarrationScripts[BehaviourKey.BRUSH_TEETH, BehaviourMode.MEDIUM], NarrationScripts[BehaviourKey.BRUSH_TEETH, BehaviourMode.OFF])
    }

    /** Squats and walking speak as they start, then leave the user to move in quiet. */
    @Test
    fun sensorStepsEndWithoutATrailingPause() {
        listOf(BehaviourKey.SQUATS, BehaviourKey.WALKING).forEach { key ->
            val start = NarrationScripts[key]
            assertTrue(start.all { it.clipRes != NarrationSegment.NO_CLIP })
            assertEquals(0L, start.last().pauseAfterMillis)
        }
    }

    /** The "nice work" after a sensor target fits inside the next behaviour instead of pushing it over. */
    @Test
    fun praiseAfterASensorTargetDoesNotLengthenTheNextBehaviour() {
        val next = NarrationScripts[BehaviourKey.TURN_ON_LIGHT, BehaviourMode.SHORT]
        val withPraise = NarrationScripts.afterSensorTarget(BehaviourKey.WALKING, next)
        assertEquals(next.size + 1, withPraise.size)
        assertTrue(withPraise.sumOf { it.pauseAfterMillis } <= next.sumOf { it.pauseAfterMillis })
        assertEquals(next.last(), withPraise.last())
    }
}

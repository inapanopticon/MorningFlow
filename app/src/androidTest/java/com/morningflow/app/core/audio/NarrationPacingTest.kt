package com.morningflow.app.core.audio

import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks every Short, Medium and Long script finishes speaking before its stage runs out.
 *
 * A stage ends on its own countdown, so narration that is still mid-sentence when the
 * clock reaches zero is cut off and the next behaviour starts talking over it. The
 * scripts used to be timed to land exactly on the budget, which left nothing for the
 * time it takes to open and start each recording - so the closing line was reliably
 * clipped on the behaviours whose final line sits right at the end.
 *
 * This has to run on a device: it measures the shipped recordings. The JVM tests can
 * only see the pauses, which is why they never caught it.
 */
@RunWith(AndroidJUnit4::class)
class NarrationPacingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun clipMillis(resId: Int): Long {
        val retriever = MediaMetadataRetriever()
        try {
            context.resources.openRawResourceFd(resId).use { fd ->
                retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong()
                ?: error("no duration for raw resource $resId")
        } finally {
            retriever.release()
        }
    }

    private val lengths = listOf(BehaviourMode.SHORT, BehaviourMode.MEDIUM, BehaviourMode.LONG)

    /** Behaviours with a fixed length per mode, as opposed to sensor and leaving steps. */
    private val timedBehaviours = BehaviourKey.entries.filter { !it.isSensor && it != BehaviourKey.LEAVING_REMINDER }

    private fun BehaviourKey.millisFor(mode: BehaviourMode): Long = 1000L * when (mode) {
        BehaviourMode.SHORT -> shortSeconds
        BehaviourMode.LONG -> longSeconds
        else -> mediumSeconds
    }

    /** When the last word of [segments] lands, measured from the recordings themselves. */
    private fun lastWordAt(segments: List<NarrationSegment>): Long =
        segments.sumOf { clipMillis(it.clipRes) } + segments.dropLast(1).sumOf { it.pauseAfterMillis }

    @Test
    fun everyLengthStopsSpeakingBeforeItsStageEnds() {
        val tooTight = mutableListOf<String>()

        timedBehaviours.forEach { key ->
            lengths.forEach { mode ->
                // The pause after the final line is silence the stage can spend however it
                // likes; what matters is when the last word lands.
                val finishesAt = lastWordAt(NarrationScripts[key, mode])
                val headroom = key.millisFor(mode) - finishesAt
                if (headroom < MINIMUM_HEADROOM_MILLIS) {
                    tooTight += "$key $mode: finishes at ${finishesAt}ms of ${key.millisFor(mode)}ms " +
                        "(${headroom}ms spare, needs $MINIMUM_HEADROOM_MILLIS)"
                }
            }
        }

        assertTrue(
            "narration runs past the end of its stage and will be cut off:\n" +
                tooTight.joinToString("\n"),
            tooTight.isEmpty(),
        )
    }

    /**
     * Squats and walking say "nice work" at the start of whatever follows them, which is
     * paid for out of that behaviour's pauses. It must still finish inside its stage.
     */
    @Test
    fun praiseAfterASensorTargetStillLeavesTheNextBehaviourRoomToFinish() {
        val tooTight = mutableListOf<String>()

        listOf(BehaviourKey.SQUATS, BehaviourKey.WALKING).forEach { sensor ->
            timedBehaviours.forEach { key ->
                lengths.forEach { mode ->
                    val segments = NarrationScripts.afterSensorTarget(sensor, NarrationScripts[key, mode])
                    val headroom = key.millisFor(mode) - lastWordAt(segments)
                    if (headroom < MINIMUM_HEADROOM_MILLIS) {
                        tooTight += "$key $mode after $sensor: ${headroom}ms spare"
                    }
                }
            }
        }

        assertTrue(
            "narration runs past the end of its stage once the praise is added:\n" +
                tooTight.joinToString("\n"),
            tooTight.isEmpty(),
        )
    }

    /** The trimmed recordings must still contain speech, not just a blip of silence. */
    @Test
    fun everyRecordingHasSpeechInIt() {
        val empty = timedBehaviours.flatMap { key -> lengths.flatMap { NarrationScripts[key, it] } }
            .filter { clipMillis(it.clipRes) < 300 }
            .map { it.text }
        assertTrue("recordings too short to hold their line:\n" + empty.joinToString("\n"), empty.isEmpty())
    }

    private companion object {
        /**
         * The floor this test enforces, deliberately below the two seconds the scripts
         * are actually written to.
         *
         * The scripts were paced against durations read with ffprobe, while this reads
         * them through [MediaMetadataRetriever]; the two can disagree by a few tens of
         * milliseconds per clip over a container's reported length. Asserting the same
         * number the scripts target would make that difference look like a failure, so
         * the gap between the two is the measurement tolerance. Anything that drops a
         * script below this floor is a real regression rather than a rounding argument.
         */
        const val MINIMUM_HEADROOM_MILLIS = 1_500L
    }
}

package com.morningflow.app.core.audio

import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode

/**
 * The narration for every behaviour.
 *
 * The recorded lines and their pauses live in [NarrationClips], generated from the
 * measured recordings: each timed behaviour has a Short, Medium and Long version that
 * share their opening and closing lines, and each version's pauses are worked out so it
 * stops speaking at least two seconds before its countdown ends. NarrationPacingTest
 * re-measures the shipped files on a device and fails if that ever stops being true.
 *
 * Sensor behaviours (squats, walking) last until their target, so they speak only as
 * they begin and stay quiet while the user moves; [afterSensorTarget] adds the short
 * "nice work" once the target is reached.
 *
 * The leaving reminder names the day's weather, plans and packing list, so it is put
 * together by [com.morningflow.app.core.domain.LeavingReminderNarration] from the
 * recorded lines below and text-to-speech for the parts that change every day.
 */
object NarrationScripts {

    /** The version of [key] for [mode]; any mode without its own recording plays Medium. */
    operator fun get(key: BehaviourKey, mode: BehaviourMode = BehaviourMode.MEDIUM): List<NarrationSegment> =
        when {
            key.isSensor -> NarrationClips.sensorStart.getValue(key)
            key == BehaviourKey.LEAVING_REMINDER -> listOf(LEAVING_OPENING, LEAVING_CLOSING)
            else -> NarrationClips.timed.getValue(key).let { it[mode] ?: it.getValue(BehaviourMode.MEDIUM) }
        }

    /**
     * [script] preceded by the "nice work" for a sensor behaviour that just reached its
     * target. The time it takes is taken back out of [script]'s own pauses, so the next
     * behaviour still finishes speaking inside its countdown.
     */
    fun afterSensorTarget(finished: BehaviourKey, script: List<NarrationSegment>): List<NarrationSegment> {
        val done = NarrationClips.sensorDone[finished] ?: return script
        val spoken = NarrationClips.sensorDoneMillis.getValue(finished) + PAUSE_AFTER_PRAISE_MILLIS
        return listOf(done.copy(pauseAfterMillis = PAUSE_AFTER_PRAISE_MILLIS)) + script.pausesShortenedBy(spoken)
    }

    private const val PAUSE_AFTER_PRAISE_MILLIS = 600L

    val LEAVING_OPENING: NarrationSegment get() = NarrationClips.LEAVING_OPENING
    val LEAVING_UMBRELLA: NarrationSegment get() = NarrationClips.LEAVING_UMBRELLA
    val LEAVING_JACKET: NarrationSegment get() = NarrationClips.LEAVING_JACKET
    val LEAVING_SUNSCREEN: NarrationSegment get() = NarrationClips.LEAVING_SUNSCREEN
    val LEAVING_SCHEDULE_INTRO: NarrationSegment get() = NarrationClips.LEAVING_SCHEDULE_INTRO
    val LEAVING_CALENDAR_CLEAR: NarrationSegment get() = NarrationClips.LEAVING_CALENDAR_CLEAR
    val LEAVING_CLOSING: NarrationSegment get() = NarrationClips.LEAVING_CLOSING

    /**
     * The same lines with [millis] taken out of their pauses, shared across them in
     * proportion so no single gap collapses on its own.
     *
     * The final pause is left alone: it is the gap between the closing line and the end
     * of the stage, and shortening it would let the closing line run into the next one.
     */
    private fun List<NarrationSegment>.pausesShortenedBy(millis: Long): List<NarrationSegment> {
        val trimmable = dropLast(1).sumOf { it.pauseAfterMillis }
        if (trimmable <= 0L) return this
        val scale = ((trimmable - millis).coerceAtLeast(0L)).toDouble() / trimmable
        return mapIndexed { index, segment ->
            if (index == lastIndex) segment
            else segment.copy(pauseAfterMillis = (segment.pauseAfterMillis * scale).toLong())
        }
    }
}

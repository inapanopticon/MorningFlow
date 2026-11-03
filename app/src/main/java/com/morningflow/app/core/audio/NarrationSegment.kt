package com.morningflow.app.core.audio

import androidx.annotation.RawRes

/**
 * One spoken line plus the silence that follows it.
 *
 * The pause is the user's working time - changing clothes, making the bed, doing
 * squats - so it is held as data rather than baked into the recording. That keeps the
 * shipped audio to the spoken minutes only, and lets a step finish early once a sensor
 * confirms it instead of waiting out a fixed silence.
 *
 * [clipRes] is [NO_CLIP] for lines that are only known at runtime, such as the leaving
 * reminder's weather and schedule; those are spoken by text-to-speech instead.
 */
data class NarrationSegment(
    @param:RawRes val clipRes: Int,
    val text: String,
    val pauseAfterMillis: Long,
) {
    companion object {
        const val NO_CLIP = 0
    }
}

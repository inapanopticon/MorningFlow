package com.morningflow.app.core.sensor.squat

import kotlin.math.sqrt

/**
 * One synchronized motion reading, in device axes, units m/s².
 *
 * - `lin*`  = TYPE_LINEAR_ACCELERATION (gravity already removed by the system)
 * - `grav*` = TYPE_GRAVITY (the most recent gravity vector)
 *
 * No Android imports on purpose: [DirectionalSquatAlgorithm] consumes only this
 * class, so it can be unit-tested on a plain JVM and replayed against recordings.
 */
data class MotionSample(
    val tMs: Long,
    val linX: Float,
    val linY: Float,
    val linZ: Float,
    val gravX: Float,
    val gravY: Float,
    val gravZ: Float,
) {
    /**
     * Signed vertical acceleration. **Positive = accelerating UPWARD.**
     *
     * Android's gravity vector points AWAY from the Earth: a phone lying face-up
     * reports roughly (0, 0, +9.81). Projecting linear acceleration onto it
     * (dot product / |g|) gives the upward component whatever way the phone is
     * held; horizontal motion projects to ~0.
     *
     * Returns 0 while gravity is still unknown (|g| ≈ 0).
     */
    val verticalUp: Float
        get() {
            val g = sqrt(gravX * gravX + gravY * gravY + gravZ * gravZ)
            if (g < 1e-3f) return 0f
            return (linX * gravX + linY * gravY + linZ * gravZ) / g
        }
}

/** Simple moving average over the last [window] values. */
internal class MovingAverage(private val window: Int) {
    init {
        require(window > 0) { "window must be > 0" }
    }

    private val buffer = FloatArray(window)
    private var index = 0
    private var filled = 0

    fun add(value: Float): Float {
        buffer[index] = value
        index = (index + 1) % window
        if (filled < window) filled++
        var sum = 0f
        for (i in 0 until filled) sum += buffer[i]
        return sum / filled
    }

    fun reset() {
        buffer.fill(0f)
        index = 0
        filled = 0
    }
}

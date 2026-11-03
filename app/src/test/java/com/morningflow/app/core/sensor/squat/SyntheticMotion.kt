package com.morningflow.app.core.sensor.squat

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds physically consistent synthetic motion at 50 Hz for unit tests.
 *
 * Squat model (vertical acceleration, up = positive):
 *   descent: a(t) = -Ad * cos(pi * t / Td)  -> starts pushing down, ends braking (positive)
 *   pause:   a(t) = 0
 *   ascent:  a(t) = +Aa * cos(pi * t / Ta)  -> starts pushing up, ends braking (negative)
 * Each phase starts and ends at zero velocity, so displacement returns to 0.
 *
 * The vertical signal is placed along an arbitrary "up" direction in device
 * coordinates, so the same squat can be replayed with the phone held flat,
 * upright or tilted.
 */
class SyntheticMotion(
    up: FloatArray = floatArrayOf(0f, 1f, 0f),
    private val noise: Float = 0.15f,
    seed: Long = 42L,
) {
    private val upUnit: FloatArray
    private val random = Random(seed)
    private var tMs = 0L
    val samples = mutableListOf<MotionSample>()

    init {
        val n = sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2])
        upUnit = floatArrayOf(up[0] / n, up[1] / n, up[2] / n)
    }

    private fun emit(verticalUp: Float, horizontal: FloatArray = FloatArray(3)) {
        val g = 9.81f
        samples += MotionSample(
            tMs = tMs,
            linX = verticalUp * upUnit[0] + horizontal[0] + gauss(),
            linY = verticalUp * upUnit[1] + horizontal[1] + gauss(),
            linZ = verticalUp * upUnit[2] + horizontal[2] + gauss(),
            gravX = g * upUnit[0],
            gravY = g * upUnit[1],
            gravZ = g * upUnit[2],
        )
        tMs += STEP_MS
    }

    private fun gauss() = (random.nextGaussian() * noise).toFloat()

    fun still(ms: Long): SyntheticMotion {
        repeat((ms / STEP_MS).toInt()) { emit(0f) }
        return this
    }

    fun squat(
        descentMs: Long = 1000,
        descentAccel: Float = 3.0f,
        pauseMs: Long = 0,
        ascentMs: Long = 800,
        ascentAccel: Float = 5.5f,
    ): SyntheticMotion {
        phase(descentMs) { f -> -descentAccel * cos(PI * f).toFloat() }
        still(pauseMs)
        phase(ascentMs) { f -> ascentAccel * cos(PI * f).toFloat() }
        return this
    }

    /** Go down and stay down (never stand up within the test). */
    fun squatDownOnly(descentMs: Long = 1000, descentAccel: Float = 3.0f): SyntheticMotion {
        phase(descentMs) { f -> -descentAccel * cos(PI * f).toFloat() }
        return this
    }

    /** A small bounce right after standing up (seen in real data after a hard stop at the top). */
    fun bounce(accel: Float = 3.5f, ms: Long = 250): SyntheticMotion {
        phase(ms) { f -> accel * sin(PI * f).toFloat() }
        return this
    }

    /** Counter-movement dip, take-off push, time in the air (free fall), landing impact. */
    fun jump(
        takeoff: Float = 14f,
        airMs: Long = 350,
        airAccel: Float = -9.81f,
        landing: Float = 20f,
    ): SyntheticMotion {
        phase(300) { f -> -3f * sin(PI * f).toFloat() }        // dip
        phase(250) { f -> takeoff * sin(PI * f).toFloat() }    // take-off
        repeat((airMs / STEP_MS).toInt()) { emit(airAccel) }   // in the air
        phase(200) { f -> landing * sin(PI * f).toFloat() }    // landing
        phase(300) { f -> -2f * sin(PI * f).toFloat() }        // settle
        return this
    }

    /** Side-to-side shaking perpendicular to "up" (e.g. waving the phone). */
    fun horizontalShake(ms: Long, amplitude: Float = 6f, hz: Float = 3f): SyntheticMotion {
        // any vector perpendicular to up
        val side = if (upUnit[0] < 0.9f) cross(upUnit, floatArrayOf(1f, 0f, 0f)) else cross(upUnit, floatArrayOf(0f, 1f, 0f))
        val start = tMs
        repeat((ms / STEP_MS).toInt()) {
            val a = amplitude * sin(2 * PI * hz * (tMs - start) / 1000.0).toFloat()
            emit(0f, floatArrayOf(side[0] * a, side[1] * a, side[2] * a))
        }
        return this
    }

    /** Fast up-down shaking along the vertical axis. */
    fun verticalShake(ms: Long, amplitude: Float = 6f, hz: Float = 4f): SyntheticMotion {
        val start = tMs
        repeat((ms / STEP_MS).toInt()) {
            emit(amplitude * sin(2 * PI * hz * (tMs - start) / 1000.0).toFloat())
        }
        return this
    }

    private fun phase(durationMs: Long, accelAt: (Double) -> Float) {
        val n = (durationMs / STEP_MS).toInt()
        for (i in 0 until n) emit(accelAt(i.toDouble() / n))
    }

    private fun cross(a: FloatArray, b: FloatArray): FloatArray {
        val c = floatArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0],
        )
        val n = sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2])
        return floatArrayOf(c[0] / n, c[1] / n, c[2] / n)
    }

    fun countWith(algorithm: DirectionalSquatAlgorithm): Int {
        algorithm.reset()
        samples.forEach(algorithm::onSample)
        return algorithm.count
    }

    companion object {
        const val STEP_MS = 20L // 50 Hz, like SENSOR_DELAY_GAME
    }
}

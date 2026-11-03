package com.morningflow.app.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Real wake-up detection: order-independent AND fusion with startup-peak filter.
 *
 * ## Detection rule
 *
 * A wake-up is confirmed when — within a single [windowMs] observation window —
 * BOTH conditions hold:
 *   1. accelerometer magnitude has exceeded [accelThreshold] within
 *      [accelStartupWindowMs] of the window opening (the "startup peak"), AND
 *   2. cumulative gyroscope rotation has reached [rotationThreshold].
 *
 * The order in which these two conditions become true does not matter.
 *
 * ## Why the startup-peak filter matters
 *
 * A naive AND (any accel peak, any rotation, any time) generates false positives
 * for "phone already in hand, slow rotation" — accel eventually crosses 3.0 from
 * incidental hand motion, and rotation trivially accumulates. Real pickups have
 * their strongest acceleration in the first ~500ms because the phone had to be
 * put into motion from rest; that early jerk is what distinguishes them from
 * a phone that was already moving in the hand.
 *
 * ## Contract with [com.morningflow.app.core.data.RealWakeUpDetection]
 *
 * This class only reports raw detections. It does not decide when to run:
 * the wake-up scheduler starts it when the Wake-up Window opens and stops it at
 * Latest or when the user starts the Session. It is independent of the
 * in-session detectors (steps, squats, light).
 *   - [onWakeUp] is invoked once per raw detection. RealWakeUpDetection offers
 *     each one to `WakeUpDetectionGate` for the 2-minute dedup.
 *   - Lifecycle: [start] / [stop] / [reset]. [pause] is kept as an alias of
 *     [stop] to match the sibling detectors.
 *
 * Threading: lifecycle methods should be called from one thread (normally main).
 * Sensor callbacks arrive on the thread the listener was registered on.
 */
class WakeUpDetector(context: Context) : SensorEventListener, WakeUpSignalSource {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    /* --------- Tunable parameters --------- */

    /** Accel magnitude that qualifies as "the phone moved". */
    var accelThreshold: Float = 3.0f

    /** Cumulative rotation (degrees) required within the window. */
    var rotationThreshold: Float = 45f

    /** Angular speed (rad/s) that qualifies as "starting to rotate". */
    var rotationOpenerRadPerSec: Float = 0.5f

    /** Observation window from the first triggering signal. */
    var windowMs: Long = 2500L

    /** Accel spike must land within this many ms of window open to count. */
    var accelStartupWindowMs: Long = 500L

    /** After firing, ignore new signals for this long. */
    var cooldownMs: Long = 2000L

    /* --------- Public state --------- */

    /** True when the device has both a linear-acceleration sensor and a gyroscope. */
    override val isSupported: Boolean
        get() = accelSensor != null && gyroSensor != null

    /** Invoked once per raw detection. Deduplication is the caller's job. */
    @Volatile
    override var onWakeUp: (() -> Unit)? = null

    /* --------- Internal state --------- */

    private var windowStartMs = 0L
    private var accelReached = false
    private var accelPeak = 0f
    private var rotationAccumulatorRad = 0f
    private var lastGyroTimestampNs = 0L
    private var lastFireMs = 0L

    /** Guards against stray callbacks delivered after unregisterListener. */
    @Volatile
    private var running = false

    /* --------- Lifecycle --------- */

    /**
     * Register accelerometer + gyroscope listeners. Returns false if the device
     * lacks either sensor or registration fails; the detector then stays inert.
     */
    override fun start(): Boolean {
        if (running) return true
        val accel = accelSensor ?: return false
        val gyro = gyroSensor ?: return false

        val accelOk = sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        val gyroOk = sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        if (!accelOk || !gyroOk) {
            // Never leave one half registered: a detection needs both sensors.
            sensorManager.unregisterListener(this)
            return false
        }
        running = true
        return true
    }

    /** Unregister. The observation window and cooldown are kept. */
    fun pause() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(this)
    }

    /** Same as [pause]; the scheduler calls this at Latest or when the Session starts. */
    override fun stop() {
        pause()
    }

    /** Unregister and clear the observation window and cooldown. */
    override fun reset() {
        pause()
        resetWindow()
        lastFireMs = 0L
    }

    /* --------- Sensor callback --------- */

    override fun onSensorChanged(event: SensorEvent) {
        // Discard stragglers delivered after unregisterListener.
        if (!running) return
        val nowMs = SystemClock.elapsedRealtime()
        if (lastFireMs != 0L && nowMs - lastFireMs < cooldownMs) return

        when (event.sensor?.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> handleAccel(event, nowMs)
            Sensor.TYPE_GYROSCOPE -> handleGyro(event, nowMs)
        }
    }

    private fun handleAccel(event: SensorEvent, nowMs: Long) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        // Expire stale window before considering this sample.
        if (windowStartMs != 0L && nowMs - windowStartMs > windowMs) {
            resetWindow()
        }

        if (magnitude > accelThreshold) {
            if (windowStartMs == 0L) {
                windowStartMs = nowMs
            }
            // Startup filter: only count the accel if it lands soon after window open.
            val ageInWindow = nowMs - windowStartMs
            if (ageInWindow <= accelStartupWindowMs) {
                accelReached = true
                if (magnitude > accelPeak) accelPeak = magnitude
            }
            tryFire(nowMs)
        }
    }

    private fun handleGyro(event: SensorEvent, nowMs: Long) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val angularSpeed = sqrt(x * x + y * y + z * z)

        if (windowStartMs != 0L && nowMs - windowStartMs > windowMs) {
            resetWindow()
        }

        if (windowStartMs == 0L) {
            // Only meaningful rotation opens a new window (filters gyro drift noise).
            if (angularSpeed > rotationOpenerRadPerSec) {
                windowStartMs = nowMs
                lastGyroTimestampNs = event.timestamp
            } else {
                return
            }
        }

        val dt: Float = if (lastGyroTimestampNs == 0L) 0f
        else (event.timestamp - lastGyroTimestampNs) / 1_000_000_000f
        lastGyroTimestampNs = event.timestamp

        // Guard against absurd dt (e.g. wake from sleep gap).
        if (dt in 0f..0.5f) {
            rotationAccumulatorRad += angularSpeed * dt
        }

        tryFire(nowMs)
    }

    private fun tryFire(nowMs: Long) {
        val degrees = Math.toDegrees(rotationAccumulatorRad.toDouble()).toFloat()
        if (accelReached && degrees >= rotationThreshold) {
            lastFireMs = nowMs
            resetWindow()
            onWakeUp?.invoke()
        }
    }

    private fun resetWindow() {
        windowStartMs = 0L
        accelReached = false
        accelPeak = 0f
        rotationAccumulatorRad = 0f
        lastGyroTimestampNs = 0L
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

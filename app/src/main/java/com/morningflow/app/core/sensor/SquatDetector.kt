package com.morningflow.app.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.morningflow.app.BuildConfig
import com.morningflow.app.core.sensor.squat.DirectionalSquatAlgorithm
import com.morningflow.app.core.sensor.squat.MotionSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Counts squats from the phone's motion sensors.
 *
 * ## How it works
 *
 * Listens to two fused sensors (neither needs a runtime permission):
 *   - TYPE_LINEAR_ACCELERATION — acceleration with gravity removed
 *   - TYPE_GRAVITY             — which way is "up" right now
 *
 * Every linear-acceleration sample is paired with the latest gravity vector and
 * handed to [DirectionalSquatAlgorithm], which projects it onto the vertical axis
 * and counts a squat only after a full "down -> bottom -> up -> stop" cycle.
 * Jumps (sustained free fall) and violent shaking are rejected. The algorithm is
 * pure Kotlin and unit-tested separately; this class is only the Android plumbing.
 *
 * The previous version thresholded |linear acceleration| only. It had no notion
 * of direction, so shaking and jumps were counted, and a pause at the bottom could
 * split one squat into two counts.
 *
 * ## Contract with [com.morningflow.app.core.data.RealSensorRepository] (unchanged)
 *
 *   - Constructor takes a [Context].
 *   - Exposes [squatCount] as a [StateFlow]<[Int]>.
 *   - Lifecycle: [start] / [pause] / [stop] / [reset].
 *   - If the device lacks either sensor, [start] returns false and the count stays
 *     at 0; SessionEngine then falls back to timed completion.
 *
 * ## Availability
 *
 * Reports whether both sensors are actually usable via [availability]. It is
 * computed at construction and re-checked on every [start], so the Session sees
 * the current state the next time a behaviour begins or resumes:
 *
 *   - AVAILABLE          — both sensors exist (and, after [start], are registered).
 *   - SENSOR_UNSUPPORTED — the device lacks TYPE_LINEAR_ACCELERATION or
 *     TYPE_GRAVITY, or registerListener failed, so squats cannot be counted
 *     right now.
 *
 * Unlike TYPE_STEP_DETECTOR, neither sensor requires ACTIVITY_RECOGNITION or any
 * other runtime permission. That's why [Availability] here only has two states,
 * not three.
 *
 * Calibrated for the phone held in both hands at chest height.
 *
 * Threading: listeners are registered without a Handler, so callbacks arrive on the
 * main thread, like the lifecycle calls. [squatCount] is a thread-safe StateFlow.
 *
 * ## Constructor injection
 *
 * [sensorManager] and [algorithm] default to the real system service and default
 * thresholds. They exist so tests can inject fakes or tuned parameters; production
 * code just calls `SquatDetector(context)`.
 */
class SquatDetector(
    context: Context,
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
    private val algorithm: DirectionalSquatAlgorithm = DirectionalSquatAlgorithm(),
) : SensorEventListener {

    companion object {
        private const val TAG = "SquatDetector"
        private const val SAMPLE_TAG = "SquatSample"
    }

    enum class Availability {
        /** Both sensors exist and can be used. */
        AVAILABLE,

        /**
         * Device lacks TYPE_LINEAR_ACCELERATION or TYPE_GRAVITY, or registering
         * the listeners failed. Squats cannot be counted right now.
         */
        SENSOR_UNSUPPORTED,
    }

    private val linearSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gravitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val _squatCount = MutableStateFlow(0)

    /** Number of counted squats since the last [reset]. Observed by RealSensorRepository. */
    val squatCount: StateFlow<Int> = _squatCount.asStateFlow()

    private val _availability = MutableStateFlow(computeAvailability())
    val availability: StateFlow<Availability> = _availability.asStateFlow()

    /** Guards against stray callbacks delivered after unregisterListener. */
    private var running = false

    private val gravity = FloatArray(3)
    private var hasGravity = false

    /* --------- Lifecycle --------- */

    /**
     * Begin (or resume) listening. Returns false if the device lacks
     * TYPE_LINEAR_ACCELERATION or TYPE_GRAVITY — the detector then stays inert.
     *
     * The count survives a pause; any half-finished movement from before the pause
     * is forgotten, because the time gap makes it meaningless.
     *
     * Re-checks availability every call. [availability] ends up AVAILABLE when
     * this returns true and SENSOR_UNSUPPORTED when it returns false, including
     * when registerListener fails and the registration is rolled back.
     */
    fun start(): Boolean {
        _availability.value = computeAvailability()
        if (running) return true
        val linear = linearSensor
        val grav = gravitySensor
        if (linear == null || grav == null) {
            Log.w(TAG, "start() ignored: linear=${linear != null} gravity=${grav != null}")
            return false
        }

        algorithm.clearMotionState()
        hasGravity = false

        val gravityOk = sensorManager.registerListener(this, grav, SensorManager.SENSOR_DELAY_GAME)
        val linearOk = sensorManager.registerListener(this, linear, SensorManager.SENSOR_DELAY_GAME)
        if (!gravityOk || !linearOk) {
            sensorManager.unregisterListener(this)
            Log.w(TAG, "registerListener failed: gravity=$gravityOk linear=$linearOk")
            // The sensors exist but cannot be used right now.
            _availability.value = Availability.SENSOR_UNSUPPORTED
            return false
        }
        running = true
        _availability.value = Availability.AVAILABLE
        Log.d(TAG, "started, squatCount=${_squatCount.value}")
        if (BuildConfig.DEBUG) Log.i(SAMPLE_TAG, "START count=${algorithm.count} params=${algorithm.params}")
        return true
    }

    /** Temporarily unregister the listeners; the count is preserved for resume. */
    fun pause() {
        if (!running) return
        sensorManager.unregisterListener(this)
        running = false
        Log.d(TAG, "paused at squatCount=${_squatCount.value}")
        if (BuildConfig.DEBUG) Log.i(SAMPLE_TAG, "STOP count=${algorithm.count}")
    }

    /** Same as [pause] for now; kept as a separate name to match sibling detectors. */
    fun stop() = pause()

    /** Clear all state; the next [start] begins a fresh count from 0. */
    fun reset() {
        pause()
        algorithm.reset()
        hasGravity = false
        _squatCount.value = 0
        Log.d(TAG, "reset")
    }

    /* --------- Sensor callbacks --------- */

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return // drop stragglers delivered after unregisterListener
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                gravity[0] = event.values[0]
                gravity[1] = event.values[1]
                gravity[2] = event.values[2]
                hasGravity = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                // Without a gravity direction there is no "vertical" yet.
                if (!hasGravity) return
                val sample = MotionSample(
                        tMs = event.timestamp / 1_000_000,
                        linX = event.values[0],
                        linY = event.values[1],
                        linZ = event.values[2],
                        gravX = gravity[0],
                        gravY = gravity[1],
                        gravZ = gravity[2],
                )
                val before = algorithm.phase
                algorithm.onSample(sample)
                if (BuildConfig.DEBUG) {
                    // Keep every sample so the exact input can be replayed offline.
                    // INFO also works on devices that suppress DEBUG log messages.
                    Log.i(SAMPLE_TAG,
                        "t_ms=${sample.tMs} timestamp_ns=${event.timestamp} " +
                            "lin_x=${sample.linX} lin_y=${sample.linY} lin_z=${sample.linZ} " +
                            "grav_x=${sample.gravX} grav_y=${sample.gravY} grav_z=${sample.gravZ} " +
                            "vertical=${sample.verticalUp} smooth=${algorithm.lastSignal} " +
                            "phase_before=$before phase=${algorithm.phase} count=${algorithm.count} " +
                            "last_rejection=${algorithm.lastRejection}")
                }
                if (algorithm.count != _squatCount.value) {
                    _squatCount.value = algorithm.count
                    Log.d(TAG, "squat counted, squatCount=${algorithm.count}")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Fused motion sensors do not report meaningful accuracy changes.
    }

    private fun computeAvailability(): Availability =
        if (linearSensor == null || gravitySensor == null) Availability.SENSOR_UNSUPPORTED
        else Availability.AVAILABLE
}

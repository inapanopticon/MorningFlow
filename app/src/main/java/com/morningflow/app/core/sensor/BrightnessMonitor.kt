package com.morningflow.app.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps Android's TYPE_LIGHT hardware sensor into a session-friendly
 * lifecycle: start / pause / resume / reset / stop.
 *
 * ## Why an ambient-light monitor at all
 *
 * The Morning Reset Module asks the user to turn on the light and/or
 * open the curtains. TYPE_LIGHT lets us check whether the room is
 * bright, so the Session can play a voice reminder once if it is dark.
 *
 * By design, brightness is only
 * *supporting* evidence — the Session continues either way. So this
 * class never blocks progression; it just publishes the current reading.
 *
 * ## Detection strategy (absolute threshold, not latched)
 *
 * The threshold was picked from on-device measurements on Huawei
 * Mate 60 Pro:
 *
 *   - Lights off, curtains closed:       5–25 lux (natural variation)
 *   - Indoor lamp on (evening):          ~80 lux
 *   - Curtains open, cloudy day:         ~50–150 lux
 *   - Curtains open, sunlight:           3000+ lux
 *
 * An absolute threshold of [THRESHOLD_LUX_DEFAULT] catches all four
 * "the room is bright" cases while staying comfortably above the
 * observed noise floor. It needs no baseline, so a room that was
 * already bright before the check reads as bright on the first sample.
 *
 * [isBright] is re-evaluated on every reading and is not latched: it
 * goes back to false if the room gets dark again. An earlier version
 * required two consecutive readings above the threshold; that was
 * removed because light is an on-change sensor, so a steady room may
 * deliver only one reading and the confirmation could stall.
 *
 * ## How the Session uses it (one-shot check)
 *
 * 1. Call [start] when the behaviour begins (Turn on Light, Open Curtains).
 * 2. Wait for the first non-null [isBright]. Android delivers the current
 *    reading right after registration.
 * 3. If it is false, play the reminder once.
 * 4. Call [stop].
 *
 * Never treat null as dark: null means "no reading yet" or "no light
 * sensor". If [availability] is SENSOR_UNSUPPORTED, or [isBright] is
 * still null after a short wait, skip the reminder.
 *
 * ## Responsibilities (kept intentionally narrow, matching StepCounter)
 *
 * - Own the SensorManager subscription for TYPE_LIGHT.
 * - Publish "is the room bright right now" via [isBright].
 * - Report whether the sensor is available via [availability].
 *
 * ## Not this class's job
 *
 * - Deciding when to start / pause / reset (SessionEngine does).
 * - Deciding what a dark or bright room means for the Module
 *   (SessionEngine does).
 * - Playing voice reminders (SessionEngine + audio layer do).
 * - Anything about steps, squats, or wake-up detection.
 *
 * ## Permissions
 *
 * Unlike TYPE_STEP_DETECTOR, TYPE_LIGHT does not require
 * ACTIVITY_RECOGNITION or any other runtime permission — Android
 * exposes ambient light freely. That's why [Availability] here only
 * has two states, not three.
 *
 * ## Constructor injection
 *
 * The [sensorManager] and [lightSensor] parameters default to the real
 * system service and hardware sensor. They exist so future unit tests
 * can inject fakes; production code just calls `BrightnessMonitor(context)`.
 *
 * ## Example
 *
 *     val monitor = BrightnessMonitor(applicationContext)
 *     monitor.isBright.value            // null: no reading yet
 *     monitor.start()                   // register listener
 *     // ...first reading arrives, 12 lux → isBright = false → play reminder
 *     // ...user opens curtains, 200 lux → isBright = true
 *     monitor.stop()                    // unregister; last value stays
 *     monitor.reset()                   // back to null, listener off
 */
class BrightnessMonitor(
    context: Context,
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
    private val lightSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT),
) : SensorEventListener {

    companion object {
        private const val TAG = "BrightnessMonitor"

        /**
         * Ambient light at or above this many lux is considered "bright".
         * Chosen from measurements on Huawei Mate 60 Pro to sit safely
         * between the observed noise ceiling (~25 lux) and the weakest
         * real-world "brightened" signal (indoor lamp on cloudy day
         * ~50 lux). See the KDoc on the class for the full table.
         */
        const val THRESHOLD_LUX_DEFAULT: Float = 40f

        /** The whole decision: a reading at or above the threshold is bright. */
        internal fun isBrightFor(lux: Float, thresholdLux: Float): Boolean =
            lux >= thresholdLux
    }

    enum class Availability {
        /** Sensor exists and can be used. */
        AVAILABLE,

        /**
         * Device has no ambient-light sensor, or registering the listener
         * failed. Brightness cannot be read right now; the next [start] checks again.
         */
        SENSOR_UNSUPPORTED,
    }

    /**
     * The lux level at or above which we count a sample as "bright".
     * Exposed as a `var` so on-device experiments can adjust it without
     * recompiling the class; production callers should leave it alone.
     */
    var thresholdLux: Float = THRESHOLD_LUX_DEFAULT

    private val _isBright = MutableStateFlow<Boolean?>(null)

    /**
     * null = no reading yet (or no light sensor), true = the latest reading
     * is at or above [thresholdLux], false = it is below.
     */
    val isBright: StateFlow<Boolean?> = _isBright.asStateFlow()

    private val _availability = MutableStateFlow(computeAvailability())
    val availability: StateFlow<Availability> = _availability.asStateFlow()

    /** Guards against stray callbacks delivered after unregisterListener. */
    private var isListening: Boolean = false

    /**
     * Begin (or resume) listening to the sensor. Called for both fresh
     * start (after [reset]) and resume (after [pause]).
     *
     * Re-checks availability every call, so a device that reports
     * SENSOR_UNSUPPORTED on first attempt but becomes usable later
     * can succeed on a subsequent call.
     */
    fun start() {
        _availability.value = computeAvailability()
        if (_availability.value != Availability.AVAILABLE) {
            Log.w(TAG, "start() ignored: availability=${_availability.value}")
            return
        }
        if (isListening) return
        val sensor = lightSensor ?: return

        // A new registration means a new check: forget the previous reading so
        // the caller waits for a fresh one. Done only here, after the guards
        // above, so a repeated start() while listening keeps the current value.
        _isBright.value = null

        // SENSOR_DELAY_NORMAL is plenty — ambient light doesn't change
        // fast enough to justify a higher rate, and higher rates would
        // burn battery for no gain. On Mate 60 Pro this yields
        // ~350 ms between samples in practice.
        val registered = sensorManager.registerListener(
            this, sensor, SensorManager.SENSOR_DELAY_NORMAL
        )
        if (!registered) {
            Log.w(TAG, "registerListener failed")
            // The sensor exists but cannot be used right now.
            _availability.value = Availability.SENSOR_UNSUPPORTED
            return
        }
        isListening = true
        Log.d(TAG, "registerListener returned $registered, thresholdLux=$thresholdLux")
    }

    /**
     * Stop listening but keep the last [isBright] value. A subsequent
     * [start] clears it and waits for a fresh reading.
     */
    fun pause() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
        Log.d(TAG, "paused; isBright=${_isBright.value}")
    }

    /**
     * Terminal stop. Behaviour matches [pause] today; the separate
     * method exists so callers can express "we are done with this
     * monitor" vs "hold on for a bit".
     */
    fun stop() = pause()

    /**
     * Forget the last reading and stop listening. The next [start]
     * begins observation from a clean slate.
     */
    fun reset() {
        if (isListening) sensorManager.unregisterListener(this)
        isListening = false
        _isBright.value = null
        Log.d(TAG, "reset")
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Discard stragglers delivered after unregisterListener; without
        // this guard, a late event could change the value after we thought
        // we'd stopped.
        if (!isListening) return
        if (event.sensor.type != Sensor.TYPE_LIGHT) return

        // TYPE_LIGHT reports a single lux value in values[0].
        val lux = event.values[0]
        val bright = isBrightFor(lux, thresholdLux)
        if (_isBright.value != bright) {
            Log.d(TAG, "isBright -> $bright (lux=$lux, threshold=$thresholdLux)")
        }
        _isBright.value = bright
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // TYPE_LIGHT does not report meaningful accuracy changes.
    }

    private fun computeAvailability(): Availability =
        if (lightSensor == null) Availability.SENSOR_UNSUPPORTED
        else Availability.AVAILABLE
}

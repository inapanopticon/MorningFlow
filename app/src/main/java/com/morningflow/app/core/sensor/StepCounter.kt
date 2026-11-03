package com.morningflow.app.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.os.SystemClock
import com.morningflow.app.BuildConfig
import com.morningflow.app.core.permissions.AndroidPermissionChecker
import com.morningflow.app.core.permissions.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Wraps Android's TYPE_STEP_DETECTOR hardware sensor into a session-friendly
 * lifecycle: start / pause / resume / reset / stop.
 *
 * ## Why STEP_DETECTOR, not STEP_COUNTER
 *
 * TYPE_STEP_COUNTER is designed for long-term totals (fitness dashboards):
 * it emits "cumulative steps since device boot" and may batch events every
 * few seconds to save power. That produced two visible defects during real-
 * device testing: (1) noticeable lag at the start of a walk, and (2) events
 * arriving in bursts, making the on-screen count jump by 5-10 at once.
 *
 * TYPE_STEP_DETECTOR is designed for exactly our case: short, real-time step
 * tracking. It emits one event per detected step (~within 2 s of each step),
 * so we simply increment a counter. The Android docs recommend it for step
 * tracking under 5 minutes — which is what a Walking Module is.
 *
 * ## Responsibilities (kept intentionally narrow)
 *
 * - Own the SensorManager subscription for TYPE_STEP_DETECTOR.
 * - Publish "steps counted since the last reset" via [stepCount].
 * - Report whether the sensor + permission are actually usable via
 *   [availability]. Availability is re-checked on every [start], so the
 *   Session sees a revoked permission the next time a behaviour begins or
 *   resumes (Android kills the app process when a runtime permission is
 *   revoked in Settings, so it cannot disappear mid-count).
 *
 * ## Not this class's job
 *
 * - Deciding when to start / pause / reset (the SessionEngine does).
 * - Comparing against a target (the SessionEngine does).
 * - Requesting runtime permission (the UI layer does; this class only
 *   passively checks whether it is currently granted).
 * - Anything about squats, brightness, or wake-up detection.
 *
 * ## How pause-time exclusion works (compare with the old STEP_COUNTER version)
 *
 * Because STEP_DETECTOR emits one event per step in real time, pause is
 * self-contained: unregistering the listener means we stop receiving events,
 * so any steps the user takes during a pause are simply never delivered to
 * us. No baseline math, no "recompute on resume" — [stepCount] naturally
 * picks up where it left off when [start] re-registers the listener.
 *
 * The only remaining subtlety is that Android may deliver a small number of
 * stragglers immediately after unregisterListener. The [isListening] guard
 * inside [onSensorChanged] discards those so they can't inflate the count.
 *
 * ## Constructor injection
 *
 * The [sensorManager], [stepSensor] and [permissionChecker] parameters default
 * to the real system service, hardware sensor and shared permission checker.
 * They exist so future unit tests can inject fakes; production code just calls
 * `StepCounter(context)`.
 *
 * ## Example
 *
 *     val counter = StepCounter(applicationContext)
 *     counter.stepCount.value          // 0
 *     counter.start()                  // register listener
 *     // ...user walks 10 steps → 10 events → count = 10
 *     counter.pause()                  // unregister
 *     // ...user walks 5 pause-time steps → no events delivered
 *     counter.start()                  // resume
 *     // ...user walks 3 more steps → 3 events → count = 13
 *     counter.reset()                  // back to 0, listener off
 */
class StepCounter(
    context: Context,
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
    private val stepSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR),
    private val permissionChecker: PermissionChecker = AndroidPermissionChecker(context),
    private val elapsedRealtimeNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
) : SensorEventListener {

    companion object {
        private const val TAG = "StepCounter"
        private const val INITIAL_GUARD_NS = 100_000_000L
    }

    enum class Availability {
        /** Sensor exists and, if needed, ACTIVITY_RECOGNITION is granted. */
        AVAILABLE,

        /**
         * Device has no hardware step detector, or registering the listener
         * failed. Steps cannot be counted right now; the next [start] checks again.
         */
        SENSOR_UNSUPPORTED,

        /**
         * Sensor exists but ACTIVITY_RECOGNITION is not granted (Android 10+).
         * The UI layer needs to request the permission; this class only
         * observes whether it is currently granted.
         */
        PERMISSION_NOT_GRANTED,
    }

    private val _stepCount = MutableStateFlow(0)
    val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

    private val _availability = MutableStateFlow(computeAvailability())
    val availability: StateFlow<Availability> = _availability.asStateFlow()

    /** Guards against stray callbacks delivered after unregisterListener. */
    private var isListening: Boolean = false
    private var needsInitialGuard = true
    private var ignoreEventsUntilNs = 0L

    private inline fun trace(message: () -> String) {
        if (BuildConfig.DEBUG) Log.i("StepSample", message())
    }

    /**
     * Begin (or resume) listening to the sensor. Called for both fresh
     * start (after [reset]) and resume (after [pause]). A fresh start ignores
     * deliveries for 100 ms; resume preserves the count without that guard.
     *
     * Re-checks availability every call, so a user who granted permission
     * after the first attempt failed can succeed on a later attempt.
     */
    fun start() {
        _availability.value = computeAvailability()
        if (_availability.value != Availability.AVAILABLE) {
            Log.w(TAG, "start() ignored: availability=${_availability.value}")
            return
        }
        if (isListening) return
        val sensor = stepSensor ?: return
        val listeningStartedNs = elapsedRealtimeNanos()

        // SENSOR_DELAY_NORMAL is a hint; STEP_DETECTOR fires per detected
        // step (~within 2 s of taking it) regardless of this rate.
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
        ignoreEventsUntilNs = if (needsInitialGuard) listeningStartedNs + INITIAL_GUARD_NS else 0L
        needsInitialGuard = false
        trace { "START count=${_stepCount.value} initial_guard=${ignoreEventsUntilNs != 0L}" }
    }

    /**
     * Stop listening but keep the current [stepCount]. A subsequent [start]
     * will pick up from wherever we paused; steps the user took during the
     * pause are excluded automatically because we simply don't receive
     * events while unregistered.
     */
    fun pause() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
        ignoreEventsUntilNs = 0L
        trace { "PAUSE count=${_stepCount.value}" }
    }

    /**
     * Terminal stop. Behaviour matches [pause] today; the separate method
     * is here so callers can express "we are done with this counter" vs
     * "hold on for a bit".
     */
    fun stop() = pause()

    /**
     * Clear counter to 0 and stop listening. The next [start] will count
     * from 0.
     */
    fun reset() {
        if (isListening) sensorManager.unregisterListener(this)
        isListening = false
        needsInitialGuard = true
        ignoreEventsUntilNs = 0L
        _stepCount.value = 0
        trace { "RESET" }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val receivedNs = elapsedRealtimeNanos()
        // Discard stragglers delivered after unregisterListener; without
        // this guard, late events would silently inflate the count.
        if (!isListening) return
        if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return

        // Some devices deliver a motion event immediately on first subscription,
        // with unusable event timestamps. Use local delivery time, not event.timestamp.
        // Reset arms this once per behaviour; pause/resume never rearms it.
        if (receivedNs < ignoreEventsUntilNs) {
            trace { "FILTERED_INITIAL" }
            return
        }

        // Every STEP_DETECTOR event represents exactly one detected step;
        // event.values[0] is always 1.0f — we don't need to read it.
        // update{} is thread-safe against concurrent updates.
        _stepCount.update { it + 1 }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // TYPE_STEP_DETECTOR does not report meaningful accuracy changes.
    }

    private fun computeAvailability(): Availability {
        if (stepSensor == null) return Availability.SENSOR_UNSUPPORTED
        // Same spec the UI requests, so the permission screen and the sensor always agree.
        if (!permissionChecker.hasAll(SensorPermissionSpec.requiredPermissions)) {
            return Availability.PERMISSION_NOT_GRANTED
        }
        return Availability.AVAILABLE
    }
}

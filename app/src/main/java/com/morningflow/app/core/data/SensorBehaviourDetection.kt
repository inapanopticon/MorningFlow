package com.morningflow.app.core.data

import android.content.Context
import com.morningflow.app.core.domain.runtime.BehaviourDetection
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.sensor.SquatDetector
import com.morningflow.app.core.sensor.StepCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A sensor that counts something for one Behaviour (squats, steps).
 *
 * [SensorBehaviourDetection] talks to sensors only through this, so it can be unit-tested
 * with fakes. [SquatCountingSensor] and [StepCountingSensor] wrap the real detectors.
 */
interface CountingSensor {
    /** Confirmed count since the last [reset]. Kept across [pause] / [start]. */
    val count: StateFlow<Int>

    /** True when the sensor can count right now (hardware present, permission granted, started). */
    val isAvailable: Boolean

    /** Starts or resumes counting. Does not clear the count. */
    fun start()

    /** Stops counting; the count is kept. */
    fun pause()

    /** Stops counting; the count is kept. */
    fun stop()

    /** Stops counting and clears the count to 0. */
    fun reset()
}

/** [CountingSensor] backed by [SquatDetector]. */
class SquatCountingSensor(private val detector: SquatDetector) : CountingSensor {
    override val count: StateFlow<Int> get() = detector.squatCount
    override val isAvailable: Boolean
        get() = detector.availability.value == SquatDetector.Availability.AVAILABLE

    override fun start() {
        detector.start()
    }

    override fun pause() = detector.pause()
    override fun stop() = detector.stop()
    override fun reset() = detector.reset()
}

/** [CountingSensor] backed by [StepCounter]. Reports unavailable when the permission is missing. */
class StepCountingSensor(private val counter: StepCounter) : CountingSensor {
    override val count: StateFlow<Int> get() = counter.stepCount
    override val isAvailable: Boolean
        get() = counter.availability.value == StepCounter.Availability.AVAILABLE

    override fun start() = counter.start()
    override fun pause() = counter.pause()
    override fun stop() = counter.stop()
    override fun reset() = counter.reset()
}

/**
 * Connects the session runtime to the squat and step sensors.
 *
 * The runtime drives one Behaviour at a time through [BehaviourDetection]. This class turns
 * that into calls on the matching sensor, and reports every count change back through
 * [report] (normally `SessionRuntime::onSensor`):
 *
 *  - [begin]: stops whatever was running, picks the sensor for the key (SQUATS → squats,
 *    WALKING → steps, anything else → nothing), resets it to 0 and starts it. The current
 *    count and availability are reported straight away, then again on every change.
 *  - [pause]: pauses the sensor; the count is kept and nothing is reported while paused.
 *  - [resume]: starts the same sensor again without a reset, then reports the cumulative
 *    count for this Behaviour (not the increment since the resume). Availability is
 *    re-checked, so a missing permission is reported as available = false.
 *  - [stop]: stops the sensor and forgets the token; late counts are dropped.
 *
 * Every report carries the token passed to [begin]. recognitionFailed is always false:
 * single failed attempts are deliberately not reported.
 *
 * ## Wiring (in AppContainer)
 *
 *     val detection = SensorBehaviourDetection.create(appContext)
 *     val runtime = SessionRuntime(templateRepository, detection = detection)
 *     detection.report = runtime::onSensor
 *
 * The runtime needs the detection when it is built and the detection reports to the
 * runtime, so [report] is set after both exist.
 *
 * Threading: call all methods on the main thread. Counts are collected on [scope], which
 * defaults to the main dispatcher, so [report] is always invoked on the main thread, as the
 * runtime requires.
 *
 * Ambient light is not handled here: it is a one-shot reminder, not a counted target. Use
 * [com.morningflow.app.core.sensor.BrightnessMonitor] directly.
 */
class SensorBehaviourDetection(
    private val squats: CountingSensor,
    private val steps: CountingSensor,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : BehaviourDetection {

    /** Receives (token, cumulativeCount, available, recognitionFailed). Set once the runtime exists. */
    var report: ((token: Long, cumulativeCount: Int, available: Boolean, recognitionFailed: Boolean) -> Unit)? =
        null

    private var current: CountingSensor? = null
    private var currentToken: Long? = null
    private var forwarding: Job? = null

    override fun begin(key: BehaviourKey, token: Long) {
        stop()
        val sensor = when (key) {
            BehaviourKey.SQUATS -> squats
            BehaviourKey.WALKING -> steps
            else -> return
        }
        current = sensor
        currentToken = token
        sensor.reset()
        sensor.start()
        forward(sensor, token)
    }

    override fun pause() {
        forwarding?.cancel()
        forwarding = null
        current?.pause()
    }

    override fun resume() {
        val sensor = current ?: return
        val token = currentToken ?: return
        sensor.start()
        forward(sensor, token)
    }

    override fun stop() {
        forwarding?.cancel()
        forwarding = null
        current?.stop()
        current = null
        currentToken = null
    }

    /** Reports the current count now (a StateFlow emits it on collect), then every change. */
    private fun forward(sensor: CountingSensor, token: Long) {
        forwarding?.cancel()
        forwarding = scope.launch {
            sensor.count.collect { count ->
                if (currentToken != token || current !== sensor) return@collect
                report?.invoke(token, count, sensor.isAvailable, false)
            }
        }
    }

    companion object {
        /** Builds the adapter on the real squat and step detectors. */
        fun create(context: Context): SensorBehaviourDetection =
            SensorBehaviourDetection(
                squats = SquatCountingSensor(SquatDetector(context)),
                steps = StepCountingSensor(StepCounter(context)),
            )
    }
}

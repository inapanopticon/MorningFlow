package com.morningflow.app.core.data

import android.content.Context
import com.morningflow.app.core.sensor.BrightnessMonitor
import com.morningflow.app.core.sensor.SquatDetector
import com.morningflow.app.core.sensor.StepCounter
import com.morningflow.app.core.model.SensorSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Real implementation of [SensorRepository] backed by hardware sensors.
 *
 * Wired: stepCount ([StepCounter]), squatCount ([SquatDetector]) and
 * brightnessChanged ([BrightnessMonitor]).
 *
 * Wake-up detection is NOT part of this class: it has its own lifecycle, driven by
 * the wake-up scheduler, and lives in [RealWakeUpDetection]. The snapshot's
 * `wakeUpDetected` / `wakeUpDetectedAt` fields are no longer written and stay at
 * their defaults; they remain only so existing code keeps compiling, and can be
 * removed from SensorSnapshot once nothing reads them.
 *
 * ## What this class does
 *
 * - Owns the in-session sensor sub-modules (StepCounter, SquatDetector,
 *   BrightnessMonitor).
 * - Forwards SessionEngine's lifecycle calls (start/pause/stop/reset)
 *   to each in-session sub-module.
 * - Combines each sub-module's state into one [SensorSnapshot] that
 *   SessionEngine can read via [snapshot].
 *
 * ## What this class does NOT do
 *
 * - No sensor algorithms live here. All algorithm work is inside the
 *   sub-modules.
 * - No permission requests. Sub-modules passively check permission and
 *   report absence by refusing to start; asking the user to grant
 *   permission is UI's job.
 * - No wake-up detection (see [RealWakeUpDetection]). Nothing here starts,
 *   stops or resets it.
 */
class RealSensorRepository(
    context: Context,
) : SensorRepository {

    // Own coroutine scope for the internal collectors below. SupervisorJob
    // means a failure in one collector will not cancel the others.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // -- Sensor sub-modules --------------------------------------------------

    private val stepCounter = StepCounter(context)
    private val squatDetector = SquatDetector(context)
    private val brightnessMonitor = BrightnessMonitor(context)

    // -- Combined snapshot ---------------------------------------------------

    private val _snapshot = MutableStateFlow(SensorSnapshot())
    override val snapshot: StateFlow<SensorSnapshot> = _snapshot.asStateFlow()

    init {
        scope.launch {
            stepCounter.stepCount.collect { steps ->
                _snapshot.update { it.copy(stepCount = steps) }
            }
        }
        scope.launch {
            squatDetector.squatCount.collect { squats ->
                _snapshot.update { it.copy(squatCount = squats) }
            }
        }
        scope.launch {
            brightnessMonitor.isBright.collect { isBright ->
                _snapshot.update { it.copy(brightnessChanged = (isBright == true)) }
            }
        }
    }

    // -- Session-scoped lifecycle: forward to in-session sub-modules --------

    /**
     * SessionEngine calls this when the current Module needs sensors. We
     * forward to the in-session sub-modules; each internally checks its own
     * availability and decides whether to actually engage.
     */
    override fun startDemoSignals() {
        stepCounter.start()
        squatDetector.start()
        brightnessMonitor.start()
    }

    override fun pause() {
        stepCounter.pause()
        squatDetector.pause()
        brightnessMonitor.pause()
    }

    override fun stop() {
        stepCounter.stop()
        squatDetector.stop()
        brightnessMonitor.stop()
    }

    override fun reset() {
        stepCounter.reset()
        squatDetector.reset()
        brightnessMonitor.reset()

        // Defense in depth: explicitly clear the whole snapshot so any
        // stale field from a sub-module that didn't emit fresh state
        // still gets reset.
        _snapshot.value = SensorSnapshot()
    }
}

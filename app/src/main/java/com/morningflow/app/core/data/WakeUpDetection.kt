package com.morningflow.app.core.data

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * What wake-up detection currently knows.
 *
 * @property available true when the device can detect wake-ups (it has the motion
 *   sensors, and the last [WakeUpDetection.start] succeeded). Never infer this from
 *   [detectedAt] being null: null only means "nothing detected yet".
 * @property detectedAt when the most recent distinct wake-up was detected, or null when
 *   nothing has been detected since the last [WakeUpDetection.reset]. Detections less than
 *   two minutes apart count as the same wake-up and keep the first timestamp, so a new
 *   value always means a new wake-up. The value stays after [WakeUpDetection.stop].
 */
data class WakeUpState(
    val available: Boolean = false,
    val detectedAt: Instant? = null,
)

/**
 * Wake-up detection, driven by the wake-up scheduler.
 *
 * The scheduler decides when detection runs (from the shared Wake-up Window): it calls
 * [start] when the window opens and [stop] at Latest or when the user really starts the
 * Session. Nothing here reads the window or starts on its own.
 *
 * Fully independent of the in-session sensors: Session start, pause, stop and reset never
 * touch wake-up detection, and [reset] here never touches steps, squats or light.
 *
 * Call all methods from one thread (normally main). [state] can be read from any thread.
 */
interface WakeUpDetection {
    val state: StateFlow<WakeUpState>

    /** Starts listening. Returns false (and reports unavailable) when the device cannot detect. */
    fun start(): Boolean

    /** Stops listening. The last [WakeUpState.detectedAt] is kept. */
    fun stop()

    /**
     * Forgets the last detection (for example when the local date changes). Does not change
     * whether detection is running.
     */
    fun reset()
}

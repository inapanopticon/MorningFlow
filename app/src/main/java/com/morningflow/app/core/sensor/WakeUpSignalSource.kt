package com.morningflow.app.core.sensor

/**
 * The raw wake-up signal, before any deduplication: "the phone was just picked up".
 *
 * [WakeUpDetector] is the real implementation. The interface exists so that
 * [com.morningflow.app.core.data.RealWakeUpDetection] can be unit-tested with a fake source
 * instead of real hardware.
 */
interface WakeUpSignalSource {
    /** True when the device has the hardware this source needs. */
    val isSupported: Boolean

    /**
     * Called once for every raw detection, possibly from a sensor thread.
     * Raw detections can repeat every few seconds while the user moves; deduplication
     * is the caller's job.
     */
    var onWakeUp: (() -> Unit)?

    /** Starts listening. Returns false when the hardware is missing or registration failed. */
    fun start(): Boolean

    /** Stops listening. Signals arriving after this are discarded. */
    fun stop()

    /** Stops listening and clears the internal detection state. */
    fun reset()
}

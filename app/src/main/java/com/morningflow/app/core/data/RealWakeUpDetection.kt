package com.morningflow.app.core.data

import android.content.Context
import com.morningflow.app.core.sensor.WakeUpDetector
import com.morningflow.app.core.sensor.WakeUpSignalSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * [WakeUpDetection] backed by the phone's motion sensors ([WakeUpDetector]).
 *
 * Raw detections go through [WakeUpDetectionGate], so a user who keeps moving produces one
 * wake-up per two minutes rather than one every few seconds.
 */
class RealWakeUpDetection(
    private val source: WakeUpSignalSource,
    private val gate: WakeUpDetectionGate = WakeUpDetectionGate(),
) : WakeUpDetection {

    constructor(context: Context) : this(WakeUpDetector(context))

    private val _state = MutableStateFlow(WakeUpState(available = source.isSupported))
    override val state: StateFlow<WakeUpState> = _state.asStateFlow()

    /** The gate and the timestamp change together, from the sensor thread or from reset(). */
    private val lock = Any()

    @Volatile
    private var running = false

    init {
        source.onWakeUp = ::onRawDetection
    }

    override fun start(): Boolean {
        if (running) return true
        val started = source.start()
        running = started
        _state.update { it.copy(available = started) }
        return started
    }

    override fun stop() {
        running = false
        source.stop()
    }

    override fun reset() {
        val wasRunning = running
        running = false
        source.reset()
        synchronized(lock) {
            gate.reset()
            _state.update { it.copy(detectedAt = null) }
        }
        if (wasRunning) start()
    }

    private fun onRawDetection() {
        if (!running) return
        synchronized(lock) {
            val detectedAt = gate.offer() ?: return
            _state.update { it.copy(detectedAt = detectedAt) }
        }
    }
}

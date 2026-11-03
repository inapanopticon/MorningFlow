package com.morningflow.app.core.data

import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * [WakeUpDetection] without hardware, for scheduler tests and the emulator.
 *
 * Call [simulateWakeUp] where the real sensor would detect a pickup. It follows the same
 * rules as the real implementation: ignored unless running, and deduplicated by
 * [WakeUpDetectionGate].
 */
class FakeWakeUpDetection(
    private val supported: Boolean = true,
    clock: Clock = Clock.systemUTC(),
) : WakeUpDetection {

    private val gate = WakeUpDetectionGate(clock = clock)
    private val _state = MutableStateFlow(WakeUpState(available = supported))
    override val state: StateFlow<WakeUpState> = _state.asStateFlow()

    var isRunning: Boolean = false
        private set

    override fun start(): Boolean {
        isRunning = supported
        _state.update { it.copy(available = supported) }
        return supported
    }

    override fun stop() {
        isRunning = false
    }

    override fun reset() {
        gate.reset()
        _state.update { it.copy(detectedAt = null) }
    }

    /** Pretends the user just picked up the phone. */
    fun simulateWakeUp() {
        if (!isRunning) return
        val detectedAt = gate.offer() ?: return
        _state.update { it.copy(detectedAt = detectedAt) }
    }
}

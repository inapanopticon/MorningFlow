package com.morningflow.app.core.domain

import com.morningflow.app.core.sensor.BrightnessMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** null means unavailable/no reading; never infer darkness from missing evidence. */
fun interface SessionLightCheck {
    suspend fun read(): Boolean?
}

class AndroidSessionLightCheck(private val monitor: BrightnessMonitor) : SessionLightCheck {
    override suspend fun read(): Boolean? = try {
        monitor.start()
        if (monitor.availability.value != BrightnessMonitor.Availability.AVAILABLE) null
        else withTimeoutOrNull(5_000) { monitor.isBright.first { it != null } }
    } finally {
        monitor.stop()
    }
}

package com.morningflow.app.core.domain

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.sensor.BrightnessMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSessionLightCheckTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = ShadowSensor.newInstance(Sensor.TYPE_LIGHT)
    private fun monitor() = BrightnessMonitor(app, manager, sensor)

    @Test fun missingReadingTimesOutAndUnregistersInsteadOfReportingDark() = runTest {
        val monitor = monitor()
        val request = async { AndroidSessionLightCheck(monitor).read() }
        runCurrent()
        assertTrue(Shadows.shadowOf(manager).hasListener(monitor, sensor))
        advanceTimeBy(5_001); runCurrent()
        assertNull(request.await())
        assertFalse(Shadows.shadowOf(manager).hasListener(monitor, sensor))
    }

    @Test fun cancelledBehaviourUnregistersPendingLightCheck() = runTest {
        val monitor = monitor()
        val request = async { AndroidSessionLightCheck(monitor).read() }
        runCurrent()
        assertTrue(Shadows.shadowOf(manager).hasListener(monitor, sensor))
        request.cancelAndJoin()
        assertFalse(Shadows.shadowOf(manager).hasListener(monitor, sensor))
    }
}

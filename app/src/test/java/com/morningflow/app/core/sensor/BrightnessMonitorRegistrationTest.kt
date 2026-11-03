package com.morningflow.app.core.sensor

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
class BrightnessMonitorRegistrationTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val sensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor = ShadowSensor.newInstance(Sensor.TYPE_LIGHT)

    @Test
    fun failedRegistrationReportsSensorUnsupportedUntilALaterStartSucceeds() {
        val monitor = BrightnessMonitor(
            context = application,
            sensorManager = sensorManager,
            lightSensor = lightSensor,
        )
        assertEquals(BrightnessMonitor.Availability.AVAILABLE, monitor.availability.value)

        Shadows.shadowOf(sensorManager).setForceListenersToFail(true)
        monitor.start()

        assertEquals(BrightnessMonitor.Availability.SENSOR_UNSUPPORTED, monitor.availability.value)

        Shadows.shadowOf(sensorManager).setForceListenersToFail(false)
        monitor.start()

        assertEquals(BrightnessMonitor.Availability.AVAILABLE, monitor.availability.value)
        assertTrue(Shadows.shadowOf(sensorManager).hasListener(monitor, lightSensor))
    }
}

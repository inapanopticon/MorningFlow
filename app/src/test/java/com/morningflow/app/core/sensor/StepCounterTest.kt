package com.morningflow.app.core.sensor

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.SensorEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
class StepCounterTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val sensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = ShadowSensor.newInstance(Sensor.TYPE_STEP_DETECTOR)

    private var nowNs = 1_000_000_000L

    private fun newCounter() = StepCounter(
        context = application,
        sensorManager = sensorManager,
        stepSensor = stepSensor,
        permissionChecker = { true },
        elapsedRealtimeNanos = { nowNs },
    )

    private fun step(counter: StepCounter) {
        val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        constructor.isAccessible = true
        val event = constructor.newInstance(1)
        event.sensor = stepSensor
        event.timestamp = 499L // This device's timestamp cannot be used as a clock.
        event.values[0] = 1f
        counter.onSensorChanged(event)
    }

    @Test
    fun filtersOnlyTheFirstHundredMillisecondsOfFreshDetection() {
        val counter = newCounter()
        counter.start()
        step(counter)
        nowNs += 99_999_999L
        step(counter)
        assertEquals(0, counter.stepCount.value)
        nowNs += 1L
        step(counter)
        assertEquals(1, counter.stepCount.value)
        nowNs += 20_000_000L
        step(counter)
        assertEquals(2, counter.stepCount.value)
    }

    @Test
    fun resumeDoesNotRearmGuardEvenWhenPausedInsideInitialWindow() {
        val counter = newCounter()
        counter.start()
        nowNs += 10_000_000L
        counter.pause()
        step(counter)
        assertEquals(0, counter.stepCount.value)
        counter.start()
        step(counter)
        assertEquals(1, counter.stepCount.value)
    }

    @Test
    fun resetRearmsGuardForNextBehaviour() {
        val counter = newCounter()
        counter.start()
        nowNs += 100_000_000L
        step(counter)
        counter.reset()
        counter.start()
        step(counter)
        assertEquals(0, counter.stepCount.value)
        nowNs += 100_000_000L
        step(counter)
        assertEquals(1, counter.stepCount.value)
    }

    @Test
    fun failedRegistrationReportsSensorUnsupportedUntilALaterStartSucceeds() {
        val counter = newCounter()
        assertEquals(StepCounter.Availability.AVAILABLE, counter.availability.value)

        Shadows.shadowOf(sensorManager).setForceListenersToFail(true)
        counter.start()

        assertEquals(StepCounter.Availability.SENSOR_UNSUPPORTED, counter.availability.value)

        Shadows.shadowOf(sensorManager).setForceListenersToFail(false)
        counter.start()

        assertEquals(StepCounter.Availability.AVAILABLE, counter.availability.value)
        assertTrue(Shadows.shadowOf(sensorManager).hasListener(counter, stepSensor))
    }
}

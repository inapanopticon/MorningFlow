package com.morningflow.app.core.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessMonitorTest {

    private val threshold = 40f

    @Test
    fun readingsBelowTheThresholdAreDark() {
        assertFalse(BrightnessMonitor.isBrightFor(0f, threshold))
        assertFalse(BrightnessMonitor.isBrightFor(39.9f, threshold))
    }

    @Test
    fun readingsAtOrAboveTheThresholdAreBright() {
        assertTrue(BrightnessMonitor.isBrightFor(40f, threshold))
        assertTrue(BrightnessMonitor.isBrightFor(3000f, threshold))
    }
}

package com.morningflow.app.core.sensor

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorPermissionSpecTest {

    @Test
    fun android9AndLowerNeedsNoPermission() {
        assertEquals(emptyList<String>(), SensorPermissionSpec.requiredPermissionsFor(26)) // minSdk, Android 8.0
        assertEquals(emptyList<String>(), SensorPermissionSpec.requiredPermissionsFor(28)) // Android 9
    }

    @Test
    fun android10AndHigherNeedsActivityRecognition() {
        val expected = listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        assertEquals(expected, SensorPermissionSpec.requiredPermissionsFor(29))
        assertEquals(expected, SensorPermissionSpec.requiredPermissionsFor(36))
    }
}

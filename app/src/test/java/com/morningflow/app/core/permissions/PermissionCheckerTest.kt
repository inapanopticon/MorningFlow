package com.morningflow.app.core.permissions

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class PermissionCheckerTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun revokePermissions() {
        Shadows.shadowOf(application).denyPermissions(Manifest.permission.READ_CALENDAR)
    }

    @Test
    fun reportsGrantedOnlyWhenAllPermissionsAreGranted() {
        val checker = AndroidPermissionChecker(application)

        assertFalse(checker.hasAll(listOf(Manifest.permission.READ_CALENDAR)))

        Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)

        assertTrue(checker.hasAll(listOf(Manifest.permission.READ_CALENDAR)))
    }
}

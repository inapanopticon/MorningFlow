package com.morningflow.app.core.data.notification

import android.Manifest
import com.morningflow.app.core.data.notification.api.NotificationPermissionSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionSpecTest {
    @Test
    fun android12LAndLowerNeedNoRuntimePermission() {
        assertEquals(emptyList<String>(), NotificationPermissionSpec.requiredPermissionsFor(26))
        assertEquals(emptyList<String>(), NotificationPermissionSpec.requiredPermissionsFor(32))
    }

    @Test
    fun android13AndHigherNeedPostNotifications() {
        val expected = listOf(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(expected, NotificationPermissionSpec.requiredPermissionsFor(33))
        assertEquals(expected, NotificationPermissionSpec.requiredPermissionsFor(36))
    }
}

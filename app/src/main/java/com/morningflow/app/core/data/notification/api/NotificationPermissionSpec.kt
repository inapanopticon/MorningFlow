package com.morningflow.app.core.data.notification.api

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build

/** Runtime permission declaration owned by Notification; request UI remains in Onboarding. */
object NotificationPermissionSpec {
    @SuppressLint("InlinedApi")
    fun requiredPermissionsFor(apiLevel: Int): List<String> =
        if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
}

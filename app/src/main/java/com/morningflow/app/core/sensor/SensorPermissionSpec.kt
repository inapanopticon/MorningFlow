package com.morningflow.app.core.sensor

import android.Manifest
import android.os.Build

/**
 * Permission declaration owned by the Sensors capability; request UI stays in Onboarding.
 *
 * Only step counting needs a runtime permission (ACTIVITY_RECOGNITION, Android 10+).
 * Squats, wake-up detection and ambient light need none.
 *
 * ## For the Onboarding / Settings UI
 *
 *     val permissions = SensorPermissionSpec.requiredPermissions
 *     if (permissions.isEmpty()) {
 *         // Android 9 or lower: nothing to ask, show Granted
 *     } else {
 *         launcher.launch(permissions.toTypedArray())   // RequestMultiplePermissions()
 *     }
 *
 *     // Granted / Denied for the UI; re-check in onResume, the user can change it in Settings:
 *     permissionChecker.hasAll(SensorPermissionSpec.requiredPermissions)
 *
 * When denied, use shouldShowRequestPermissionRationale() to decide whether to ask
 * again or to send the user to the app's system settings.
 */
object SensorPermissionSpec {
    val requiredPermissions: List<String>
        get() = requiredPermissionsFor(Build.VERSION.SDK_INT)

    /** ACTIVITY_RECOGNITION became a runtime permission in Android 10 (API 29). */
    internal fun requiredPermissionsFor(sdkInt: Int): List<String> =
        if (sdkInt >= Build.VERSION_CODES.Q) listOf(Manifest.permission.ACTIVITY_RECOGNITION) else emptyList()
}

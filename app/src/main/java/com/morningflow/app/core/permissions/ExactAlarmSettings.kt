package com.morningflow.app.core.permissions

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Builds the user-initiated Exact Alarm special-access request when the API supports it. */
@SuppressLint("InlinedApi")
internal fun createExactAlarmSettingsIntent(
    packageName: String,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Intent? = if (sdkInt >= Build.VERSION_CODES.S) {
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:$packageName")
    }
} else {
    null
}

/** Launches the special-access page without crashing when an OEM provides no handler. */
internal fun launchExactAlarmSettings(
    intent: Intent?,
    launch: (Intent) -> Unit,
): Boolean {
    if (intent == null) return false
    return try {
        launch(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

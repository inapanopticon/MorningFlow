package com.morningflow.app.core.wakeup

import android.content.Context
import android.content.Intent
import android.util.Log

/** Stops the active detection Service without touching the next scheduled Alarm. */
class AndroidWakeDetectionRuntimeController internal constructor(
    context: Context,
    private val stopService: (Intent) -> Boolean,
) : WakeDetectionRuntimeController {
    private val appContext = context.applicationContext

    constructor(context: Context) : this(
        context = context,
        stopService = { intent -> context.applicationContext.stopService(intent) },
    )

    override fun stopCurrentRun(): Boolean = try {
        stopService(Intent(appContext, WakeDetectionService::class.java))
    } catch (exception: RuntimeException) {
        Log.w(TAG, "Unable to stop the current wake-detection run", exception)
        false
    }

    private companion object {
        const val TAG = "WakeDetectionRuntime"
    }
}

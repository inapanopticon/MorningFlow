package com.morningflow.app.core.wakeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.morningflow.app.app.MorningFlowApplication
import java.time.Clock
import kotlinx.coroutines.Job

/** Turns the exact START Alarm into one bounded detection Service run. */
class WakeAlarmReceiver(
    private val requestNextAlarm: (Context) -> Job? = { context ->
        (context.applicationContext as? MorningFlowApplication)
            ?.requestWakeupScheduleRecovery()
    },
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WakeAlarmContract.ACTION_START) return

        val snapshot = WakeAlarmContract.readSnapshot(intent) ?: return

        // Re-arm from ready persisted Settings through the same serialized Coordinator used
        // by Settings and recovery events. The delivered snapshot remains authoritative only
        // for this detection run, so an old Alarm cannot overwrite a newly saved schedule.
        requestNextAlarmSafely(context)

        val clock = Clock.systemDefaultZone()
        if (!snapshot.contains(clock.instant(), clock.zone)) return

        try {
            ContextCompat.startForegroundService(
                context,
                WakeDetectionService.startIntent(context, snapshot),
            )
        } catch (exception: RuntimeException) {
            // Exact alarms are normally exempt from background FGS start restrictions, but
            // a platform or permission failure must not crash the broadcast process.
            Log.e(TAG, "Unable to start wake detection service", exception)
        }
    }

    private fun requestNextAlarmSafely(context: Context) {
        val job = try {
            requestNextAlarm(context)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to request the next wake alarm", exception)
            null
        }
        if (job == null) {
            Log.e(TAG, "Wake-up schedule coordinator is unavailable")
            return
        }

        val pendingResult = goAsync()
        job.invokeOnCompletion { error ->
            if (error != null) {
                Log.e(TAG, "Unable to reconcile the next wake alarm", error)
            }
            pendingResult?.finish()
        }
    }

    private companion object {
        const val TAG = "WakeAlarmReceiver"
    }
}

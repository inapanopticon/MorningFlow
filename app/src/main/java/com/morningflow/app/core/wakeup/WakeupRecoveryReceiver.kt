package com.morningflow.app.core.wakeup

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.morningflow.app.app.MorningFlowApplication
import kotlinx.coroutines.Job

/** Restores the one-shot wake Alarm after system events that invalidate its assumptions. */
class WakeupRecoveryReceiver(
    private val recoveryRequest: (Context) -> Job? = { context ->
        (context.applicationContext as? MorningFlowApplication)
            ?.requestWakeupScheduleRecovery()
    },
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val job = recoveryJob(context, intent.action) ?: return
        val pendingResult = goAsync()
        job.invokeOnCompletion { pendingResult.finish() }
    }

    internal fun recoveryJob(context: Context, action: String?): Job? {
        if (action !in RECOVERY_ACTIONS) return null
        return recoveryRequest(context)
    }

    private companion object {
        val RECOVERY_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

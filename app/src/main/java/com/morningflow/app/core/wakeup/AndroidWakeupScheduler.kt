package com.morningflow.app.core.wakeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.morningflow.app.core.data.WakeupScheduleFailureReason
import com.morningflow.app.core.data.WakeupScheduleStatus
import com.morningflow.app.core.data.WakeupScheduler
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.GlobalMorningSchedule
import com.morningflow.app.core.permissions.AndroidExactAlarmAccessChecker
import com.morningflow.app.core.permissions.ExactAlarmAccessChecker
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android AlarmManager implementation for the Scheduler Core.
 *
 * This class owns one stable broadcast PendingIntent. Every successful [schedule] call
 * cancels the previous alarm and replaces it with one exact RTC_WAKEUP START alarm. It does
 * not observe Settings, start a Service, or decide what happens after detection.
 */
class AndroidWakeupScheduler(
    context: Context,
    private val alarmManager: AlarmManager = requireAlarmManager(context),
    private val calculator: WakeScheduleCalculator = WakeScheduleCalculator(),
    private val exactAlarmAccessChecker: ExactAlarmAccessChecker =
        AndroidExactAlarmAccessChecker(alarmManager),
    private val exactAlarmSetter: (Long, PendingIntent) -> Unit = { triggerAtMillis, operation ->
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            operation,
        )
    },
) : WakeupScheduler {

    private val appContext = context.applicationContext
    private val _status = MutableStateFlow<WakeupScheduleStatus>(WakeupScheduleStatus.NotScheduled)
    override val status: StateFlow<WakeupScheduleStatus> = _status.asStateFlow()

    override suspend fun schedule(settings: AppSettings): WakeupScheduleStatus =
        scheduleSnapshot(settings.schedule)

    /** Re-arms the single daily START alarm from an already validated Alarm snapshot. */
    internal fun scheduleSnapshot(schedule: GlobalMorningSchedule): WakeupScheduleStatus {
        when (val calculation = calculator.calculate(schedule)) {
            is WakeScheduleCalculation.Invalid -> {
                _status.value = if (cancelExistingAlarmSafely()) {
                    WakeupScheduleStatus.Failed(calculation.reason.toFailureReason())
                } else {
                    WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.PLATFORM_REJECTED)
                }
            }

            is WakeScheduleCalculation.Valid -> {
                val hasExactAlarmAccess = try {
                    exactAlarmAccessChecker.hasExactAlarmAccess()
                } catch (_: RuntimeException) {
                    cancelExistingAlarmSafely()
                    _status.value = WakeupScheduleStatus.Failed(
                        WakeupScheduleFailureReason.PLATFORM_REJECTED,
                    )
                    return _status.value
                }
                if (!hasExactAlarmAccess) {
                    _status.value = if (cancelExistingAlarmSafely()) {
                        WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.EXACT_ACCESS_REQUIRED)
                    } else {
                        WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.PLATFORM_REJECTED)
                    }
                    return _status.value
                }

                val operation = try {
                    startPendingIntent(schedule)
                } catch (_: RuntimeException) {
                    cancelExistingAlarmSafely()
                    _status.value = WakeupScheduleStatus.Failed(
                        WakeupScheduleFailureReason.PLATFORM_REJECTED,
                    )
                    return _status.value
                }
                try {
                    // Explicitly cancel first so replacement remains true even if a platform
                    // implementation handles equivalent PendingIntent tokens differently.
                    alarmManager.cancel(operation)
                    exactAlarmSetter(
                        calculation.nextStartAt.toInstant().toEpochMilli(),
                        operation,
                    )
                    _status.value = WakeupScheduleStatus.Scheduled
                } catch (_: SecurityException) {
                    // Permission can be revoked between the capability check and the set call.
                    cancelOperationSafely(operation)
                    _status.value = WakeupScheduleStatus.Failed(
                        WakeupScheduleFailureReason.EXACT_ACCESS_REQUIRED,
                    )
                } catch (_: RuntimeException) {
                    cancelOperationSafely(operation)
                    _status.value = WakeupScheduleStatus.Failed(
                        WakeupScheduleFailureReason.PLATFORM_REJECTED,
                    )
                }
            }
        }
        return _status.value
    }

    override suspend fun cancel(): WakeupScheduleStatus {
        _status.value = if (cancelExistingAlarmSafely()) {
            WakeupScheduleStatus.NotScheduled
        } else {
            WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.PLATFORM_REJECTED)
        }
        return _status.value
    }

    private fun startPendingIntent(schedule: GlobalMorningSchedule): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            WakeAlarmContract.REQUEST_CODE,
            WakeAlarmContract.startIntent(appContext, schedule),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancelExistingAlarmSafely(): Boolean = try {
        alarmManager.cancel(startPendingIntentWithoutExtras())
        // Phase 2 used a package-scoped implicit Intent before the Receiver existed. Cancel
        // that identity as well so an installed development build cannot retain both alarms.
        alarmManager.cancel(legacyStartPendingIntentWithoutExtras())
        true
    } catch (_: RuntimeException) {
        false
    }

    private fun cancelOperationSafely(operation: PendingIntent) {
        try {
            alarmManager.cancel(operation)
        } catch (_: RuntimeException) {
            // The failure status already reports that the platform operation was rejected.
        }
    }

    private fun startPendingIntentWithoutExtras(): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            WakeAlarmContract.REQUEST_CODE,
            WakeAlarmContract.startIntent(appContext),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun legacyStartPendingIntentWithoutExtras(): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            WakeAlarmContract.REQUEST_CODE,
            Intent(WakeAlarmContract.ACTION_START).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        fun requireAlarmManager(context: Context): AlarmManager =
            requireNotNull(context.getSystemService(AlarmManager::class.java)) {
                "AlarmManager is unavailable"
            }
    }
}

private fun InvalidWakeScheduleReason.toFailureReason(): WakeupScheduleFailureReason = when (this) {
    InvalidWakeScheduleReason.ZERO_LENGTH_WINDOW -> WakeupScheduleFailureReason.ZERO_LENGTH_WINDOW
    InvalidWakeScheduleReason.END_BEFORE_START -> WakeupScheduleFailureReason.END_BEFORE_START
}

/**
 * Stable identity and snapshot payload for the Phase 2 START Alarm.
 *
 * Keeping this contract next to the Alarm adapter lets the Phase 3 receiver consume the
 * exact schedule snapshot without reading Settings.
 */
internal object WakeAlarmContract {
    const val ACTION_START = "com.morningflow.app.action.START_WAKE_DETECTION"
    const val REQUEST_CODE = 0x5746
    const val EXTRA_EARLIEST = "com.morningflow.app.extra.EARLIEST_WAKE_UP"
    const val EXTRA_LATEST = "com.morningflow.app.extra.LATEST_WAKE_UP"

    fun startIntent(context: Context): Intent =
        Intent(context, WakeAlarmReceiver::class.java).setAction(ACTION_START)

    fun startIntent(context: Context, schedule: GlobalMorningSchedule): Intent =
        putSnapshot(startIntent(context), WakeScheduleSnapshot.from(schedule))

    fun putSnapshot(intent: Intent, snapshot: WakeScheduleSnapshot): Intent =
        intent
            .putExtra(EXTRA_EARLIEST, snapshot.earliest.toString())
            .putExtra(EXTRA_LATEST, snapshot.latest.toString())

    fun readSnapshot(intent: Intent?): WakeScheduleSnapshot? {
        val source = intent ?: return null
        val earliest = parseTime(source.getStringExtra(EXTRA_EARLIEST)) ?: return null
        val latest = parseTime(source.getStringExtra(EXTRA_LATEST)) ?: return null
        return WakeScheduleSnapshot(earliest, latest).takeIf { it.isValid }
    }

    private fun parseTime(value: String?): LocalTime? =
        try {
            value?.let(LocalTime::parse)
        } catch (_: DateTimeException) {
            null
        }
}

/** A Scheduler-owned copy of the window carried by an Alarm or Service Intent. */
internal data class WakeScheduleSnapshot(
    val earliest: LocalTime,
    val latest: LocalTime,
) {
    val isValid: Boolean
        get() = earliest.isBefore(latest)

    fun contains(instant: Instant, zoneId: ZoneId): Boolean {
        if (!isValid) return false
        val now = instant.atZone(zoneId).toLocalTime()
        return !now.isBefore(earliest) && now.isBefore(latest)
    }

    fun latestInstant(instant: Instant, zoneId: ZoneId): Instant =
        instant.atZone(zoneId).toLocalDate().atTime(latest).atZone(zoneId).toInstant()

    fun toSchedule(): GlobalMorningSchedule = GlobalMorningSchedule(
        earliestWakeUp = earliest,
        latestWakeUp = latest,
    )

    companion object {
        fun from(schedule: GlobalMorningSchedule): WakeScheduleSnapshot = WakeScheduleSnapshot(
            earliest = schedule.earliestWakeUp,
            latest = schedule.latestWakeUp,
        )
    }
}

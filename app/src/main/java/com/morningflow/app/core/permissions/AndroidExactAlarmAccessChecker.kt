package com.morningflow.app.core.permissions

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.os.Build

/** Android implementation of the read-only Exact Alarm capability check. */
class AndroidExactAlarmAccessChecker(
    private val alarmManager: AlarmManager,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : ExactAlarmAccessChecker {

    constructor(context: Context) : this(requireAlarmManager(context))

    override fun hasExactAlarmAccess(): Boolean =
        sdkInt < Build.VERSION_CODES.S || canScheduleExactAlarmsOnAndroid12AndLater()

    @SuppressLint("NewApi")
    private fun canScheduleExactAlarmsOnAndroid12AndLater(): Boolean =
        alarmManager.canScheduleExactAlarms()

    private companion object {
        fun requireAlarmManager(context: Context): AlarmManager =
            requireNotNull(context.applicationContext.getSystemService(AlarmManager::class.java)) {
                "AlarmManager is unavailable"
            }
    }
}

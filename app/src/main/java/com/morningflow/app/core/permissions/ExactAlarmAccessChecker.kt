package com.morningflow.app.core.permissions

/**
 * Reads the app's current Exact Alarm capability without requesting or scheduling an alarm.
 *
 * The result is a platform snapshot. Callers should read it again after returning from the
 * system Alarms & reminders screen rather than caching or persisting it.
 */
fun interface ExactAlarmAccessChecker {
    fun hasExactAlarmAccess(): Boolean
}

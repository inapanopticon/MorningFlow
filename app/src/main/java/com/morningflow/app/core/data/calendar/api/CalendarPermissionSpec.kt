package com.morningflow.app.core.data.calendar.api

import android.Manifest

/** Permission declaration owned by the Calendar feature; request UI stays in Onboarding. */
object CalendarPermissionSpec {
    val requiredPermissions: List<String> = listOf(Manifest.permission.READ_CALENDAR)
}

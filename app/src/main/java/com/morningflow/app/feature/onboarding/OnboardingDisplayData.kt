package com.morningflow.app.feature.onboarding

import androidx.annotation.DrawableRes
import com.morningflow.app.core.designsystem.time.ClockFormat
import java.time.DayOfWeek
import java.time.LocalTime

data class ColdStartDisplayData(
    @param:DrawableRes val illustration: Int,
)

enum class OnboardingFormVisualState {
    Normal,
    Error,
}

data class OnboardingFormDisplayData(
    val earliestWakeUp: LocalTime?,
    val latestWakeUp: LocalTime?,
    val readyBy: LocalTime?,
    val activeDays: Set<DayOfWeek>,
    val errors: Set<OnboardingField> = emptySet(),
)

enum class OnboardingField {
    EARLIEST_WAKE_UP,
    LATEST_WAKE_UP,
    READY_BY,
    ACTIVE_DAYS,
}

enum class PermissionAccess {
    Granted,
    Denied,
    NotAvailable,
}

data class PermissionDisplayData(
    val location: PermissionAccess,
    val calendar: PermissionAccess,
    val sensors: PermissionAccess,
    val notifications: PermissionAccess,
    val exactAlarm: PermissionAccess,
)

internal fun LocalTime?.toOnboardingTime(): String =
    this?.let(ClockFormat::format) ?: "--:--"

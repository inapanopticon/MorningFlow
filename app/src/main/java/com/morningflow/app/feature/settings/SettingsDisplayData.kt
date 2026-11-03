package com.morningflow.app.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowNavigationDestination
import com.morningflow.app.core.designsystem.time.ClockFormat
import com.morningflow.app.core.model.Appearance
import java.time.LocalTime

enum class SettingsToggleKey {
    BackgroundMusic,
    VoiceGuidance,
    AudioCues,
}

enum class SettingsPermissionStatus { Granted, Denied, NotAvailable }

internal fun SettingsPermissionStatus.isSettingsPermissionClickable(): Boolean =
    this != SettingsPermissionStatus.NotAvailable

enum class SettingsPermissionKey {
    Location,
    Calendar,
    Sensors,
    Notifications,
    AlarmsAndReminders,
    Unknown,
}

enum class SettingsPreferenceKey { Umbrella, Jacket, Sunscreen, Unknown }

enum class SettingsTimeField { EarliestWakeUp, LatestWakeUp, ReadyBy }

enum class SettingsScheduleError { ReadinessFailed, SaveFailed }

enum class SettingsSchedulerWarning(val hasOpenSettingsAction: Boolean) {
    ExactAccessRequired(hasOpenSettingsAction = true),
    PlatformRejected(hasOpenSettingsAction = false),
}

@Immutable
data class SettingsToggleDisplayData(
    val key: SettingsToggleKey,
    @param:StringRes val label: Int,
    @param:StringRes val description: Int? = null,
    val enabled: Boolean,
    val volume: Float? = null,
    val isInteractive: Boolean = true,
)

@Immutable
data class SettingsPermissionDisplayData(
    @param:StringRes val label: Int,
    val status: SettingsPermissionStatus,
    @param:StringRes val supportingText: Int? = null,
    val key: SettingsPermissionKey = SettingsPermissionKey.Unknown,
)

internal fun SettingsPermissionDisplayData.isClickable(): Boolean =
    key != SettingsPermissionKey.Unknown &&
        status.isSettingsPermissionClickable()

@Immutable
data class SettingsPreferenceDisplayData(
    @param:StringRes val label: Int,
    val value: Int? = null,
    val active: Boolean,
    val key: SettingsPreferenceKey = SettingsPreferenceKey.Unknown,
)

@Immutable
data class SettingsDisplayData(
    val appearance: Appearance = Appearance.SYSTEM,
    val earliestWakeUp: LocalTime,
    val latestWakeUp: LocalTime,
    val readyBy: LocalTime,
    val audioSettings: List<SettingsToggleDisplayData>,
    val permissions: List<SettingsPermissionDisplayData>,
    val reminderPreferences: List<SettingsPreferenceDisplayData>,
    /** What the user packs before leaving, in the order they added it. */
    val thingsToBring: List<String>,
    val selectedNavigation: MorningFlowNavigationDestination,
    val isScheduleReady: Boolean = false,
    val isEditingSchedule: Boolean = false,
    val scheduleErrors: Set<SettingsTimeField> = emptySet(),
    val scheduleError: SettingsScheduleError? = null,
    val schedulerWarning: SettingsSchedulerWarning? = null,
)

fun LocalTime.toSettingsTimeText(): String = ClockFormat.format(this)

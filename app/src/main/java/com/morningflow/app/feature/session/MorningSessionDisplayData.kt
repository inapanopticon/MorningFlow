package com.morningflow.app.feature.session

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.morningflow.app.core.designsystem.component.session.AvailabilityType
import com.morningflow.app.core.designsystem.component.session.SessionPlaybackState

data class SessionDisplayText(
    @param:StringRes val resourceId: Int,
    val formatArgs: List<Any> = emptyList(),
)

enum class SessionStageContentType {
    LeaveBed,
    PhysicalWakeUp,
    Routine,
    LeavingReminder,
}

data class LeavingReminderItemDisplayData(
    @param:DrawableRes val icon: Int,
    val title: SessionDisplayText,
    val description: SessionDisplayText,
)

data class LeavingReminderScheduleItemDisplayData(
    val time: String? = null,
    val title: SessionDisplayText,
)

data class LeavingReminderDisplayData(
    val locationWeather: SessionDisplayText,
    val reminders: List<LeavingReminderItemDisplayData>,
    val thingsToBring: List<SessionDisplayText>,
    val scheduleItems: List<LeavingReminderScheduleItemDisplayData>,
)

data class SessionAvailabilityDisplayData(
    val type: AvailabilityType,
    val message: SessionDisplayText,
)

data class SessionDialogDisplayData(
    val title: SessionDisplayText,
    val description: SessionDisplayText? = null,
    val confirmLabel: SessionDisplayText,
    val cancelLabel: SessionDisplayText,
)

data class MorningSessionDisplayData(
    val stageContentType: SessionStageContentType,
    val stepText: SessionDisplayText,
    val stageTitle: SessionDisplayText,
    val heading: SessionDisplayText,
    val guidanceBody: SessionDisplayText?,
    val timerText: SessionDisplayText? = null,
    val countText: SessionDisplayText? = null,
    val progressText: SessionDisplayText? = null,
    val displayLineCount: Int = 2,
    @param:DrawableRes val illustration: Int,
    val availabilityMessages: List<SessionAvailabilityDisplayData> = emptyList(),
    val controlState: SessionPlaybackState,
    val isControlBarVisible: Boolean = true,
    val isDialogVisible: Boolean = false,
    val dialogCopy: SessionDialogDisplayData? = null,
    val leavingReminder: LeavingReminderDisplayData? = null,
    /** How far through the whole routine, 0..1, counting finished steps of the current stage. */
    val sessionProgress: Float = 0f,
) {
    val secondaryDisplayText: SessionDisplayText?
        get() = timerText ?: countText ?: progressText
}

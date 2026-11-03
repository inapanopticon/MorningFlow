package com.morningflow.app.feature.templates

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.morningflow.app.core.designsystem.time.ClockFormat
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.RoutineTemplate
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.math.roundToInt

enum class TemplateDetailVariant { Normal, DeleteConfirmation }

enum class TemplateEditorVariant { Normal, Error }

@Immutable
data class TemplateBehaviorDisplayData(
    val label: String,
    val value: String,
    @param:StringRes val supportingText: Int? = null,
    val key: BehaviourKey? = null,
)

@Immutable
data class TemplateTargetDisplayData(
    @param:StringRes val label: Int,
    val value: Int,
)

@Immutable
data class TemplateModuleDisplayData(
    val title: String,
    val durationMinutes: Int,
    val behaviors: List<TemplateBehaviorDisplayData>,
    val enabled: Boolean = true,
    val alwaysOn: Boolean = false,
    val moveUpEnabled: Boolean = true,
    val moveDownEnabled: Boolean = true,
    val targets: List<TemplateTargetDisplayData> = emptyList(),
    val type: ModuleType? = null,
)

@Immutable
data class TemplateSummaryDisplayData(
    val id: String = "",
    val sequence: Int,
    val title: String,
    val modules: List<TemplateModuleDisplayData>,
    val applicableDays: List<DayOfWeek>,
    val isDefault: Boolean = false,
    val isDeletable: Boolean = false,
    val plannedSeconds: Int = 0,
) {
    val moduleCount: Int get() = modules.count { it.enabled }
    val totalDurationMinutes: Int
        get() = ((if (plannedSeconds > 0) plannedSeconds else modules.sumOf { it.durationMinutes * 60 }) / 60.0).roundToInt()
}

@Immutable
data class TemplateEditorDisplayData(
    val template: TemplateSummaryDisplayData,
    val recommendedFrom: LocalTime,
    val priority: Int,
    val isDefault: Boolean,
    val recommendedFromText: String = recommendedFrom.toTemplateTimeText(),
    val priorityText: String = priority.toString(),
    val isDeletable: Boolean = false,
    val applicableDaysError: String? = null,
    val recommendedFromError: String? = null,
    val squatTargetError: String? = null,
    val walkingTargetError: String? = null,
    val templateNameErrorText: String? = null,
    val priorityErrorText: String? = null,
)

@Immutable
data class TemplateDeleteDialogDisplayData(
    @param:StringRes val title: Int,
    @param:StringRes val confirmLabel: Int,
    @param:StringRes val cancelLabel: Int,
)

fun LocalTime.toTemplateTimeText(): String = ClockFormat.format(this)

fun Int.toEditorDurationText(): String = "$this min"

fun Int.toStageDurationText(): String = "$this min"

fun List<DayOfWeek>.toTemplateDaysText(): String = joinToString(" · ") { it.shortLabel }

fun List<DayOfWeek>.toTemplateDayRangeText(): String = when (this) {
    listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    ) -> "Mon-Fri"
    else -> toTemplateDaysText()
}

fun RoutineTemplate.toTemplateSummaryDisplayData(sequence: Int = 0): TemplateSummaryDisplayData {
    val orderedModules = ModuleType.entries.mapNotNull { type ->
        modules.firstOrNull { it.type == type }?.let { module ->
            TemplateModuleDisplayData(
                type = type,
                title = type.displayName,
                durationMinutes = (module.plannedSeconds / 60.0).roundToInt(),
                behaviors = module.behaviours.map { behaviour ->
                    TemplateBehaviorDisplayData(
                        key = behaviour.key,
                        label = behaviour.label,
                        value = behaviour.mode.displayText(behaviour.plannedSeconds(module.squatTarget, module.stepTarget)),
                    )
                },
                enabled = module.enabled,
                alwaysOn = type == ModuleType.LEAVE_BED,
                moveUpEnabled = false,
                moveDownEnabled = false,
                targets = buildList {
                    if (module.behaviours.any { it.key == BehaviourKey.SQUATS }) {
                        add(TemplateTargetDisplayData(com.morningflow.app.R.string.template_squat_target, module.squatTarget))
                    }
                    if (module.behaviours.any { it.key == BehaviourKey.WALKING }) {
                        add(TemplateTargetDisplayData(com.morningflow.app.R.string.template_step_target, module.stepTarget))
                    }
                },
            )
        }
    }
    return TemplateSummaryDisplayData(
        id = id,
        sequence = sequence,
        title = name,
        modules = orderedModules,
        applicableDays = applicableDays.sortedBy { it.value },
        isDefault = isDefault,
        isDeletable = isDeletable,
        plannedSeconds = plannedSeconds,
    )
}

private fun BehaviourMode.displayText(seconds: Int): String = when (this) {
    BehaviourMode.SHORT -> "Short · ${seconds.toDurationText()}"
    BehaviourMode.MEDIUM -> "Medium · ${seconds.toDurationText()}"
    BehaviourMode.LONG -> "Long · ${seconds.toDurationText()}"
    BehaviourMode.ON -> "On · ${seconds.toDurationText()}"
    BehaviourMode.OFF -> "Off"
}

/** "40 sec", "2 min", "1 min 45 sec": how long a step takes, in words people use. */
internal fun Int.toDurationText(): String {
    val minutes = this / 60
    val seconds = this % 60
    return when {
        minutes == 0 -> "$seconds sec"
        seconds == 0 -> "$minutes min"
        else -> "$minutes min $seconds sec"
    }
}

val DayOfWeek.shortLabel: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase).take(3)

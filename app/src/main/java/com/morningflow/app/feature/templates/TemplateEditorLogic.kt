package com.morningflow.app.feature.templates

import com.morningflow.app.core.model.Behaviour
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode
import com.morningflow.app.core.model.GlobalMorningSchedule
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.RoutineModule
import com.morningflow.app.core.model.RoutineTemplate
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class TemplateFieldError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    NAME_EXISTS,
    DAYS_REQUIRED,
    RECOMMENDED_FROM_REQUIRED,
    RECOMMENDED_FROM_OUTSIDE_SCHEDULE,
    PRIORITY_REQUIRED,
    PRIORITY_RANGE,
    PRIORITY_CONFLICT,
    SQUAT_TARGET_RANGE,
    WALKING_TARGET_RANGE,
}

data class TemplateValidationResult(
    val errors: Map<TemplateFieldError, String> = emptyMap(),
) {
    val isValid: Boolean get() = errors.isEmpty()
}

data class TemplateDraft(
    val id: String?,
    val name: String,
    val applicableDays: Set<DayOfWeek>,
    val recommendedFrom: LocalTime?,
    val priorityText: String,
    val modules: List<RoutineModule>,
    val isDefault: Boolean,
)

fun validateTemplateDraft(
    draft: TemplateDraft,
    existing: List<RoutineTemplate>,
    schedule: GlobalMorningSchedule,
): TemplateValidationResult {
    val errors = linkedMapOf<TemplateFieldError, String>()
    val trimmedName = draft.name.trim()
    if (trimmedName.isEmpty()) errors[TemplateFieldError.NAME_REQUIRED] = "Template name is required."
    if (trimmedName.length > 25) errors[TemplateFieldError.NAME_TOO_LONG] = "Template name must be 25 characters or fewer."
    if (existing.any { it.id != draft.id && it.name.trim().equals(trimmedName, ignoreCase = true) }) {
        errors[TemplateFieldError.NAME_EXISTS] = "Template name already exists."
    }
    if (draft.applicableDays.isEmpty()) errors[TemplateFieldError.DAYS_REQUIRED] = "Select at least one day."

    val recommendedFrom = draft.recommendedFrom
    if (recommendedFrom == null) {
        errors[TemplateFieldError.RECOMMENDED_FROM_REQUIRED] = "Recommended From is required."
    } else if (recommendedFrom.isBefore(schedule.earliestWakeUp) || recommendedFrom.isAfter(schedule.latestWakeUp)) {
        errors[TemplateFieldError.RECOMMENDED_FROM_OUTSIDE_SCHEDULE] =
            "Recommended From must be within the wake-up window."
    }

    val priority = draft.priorityText.toIntOrNull()
    if (priority == null) {
        errors[TemplateFieldError.PRIORITY_REQUIRED] = "Priority is required."
    } else if (priority !in 1..5) {
        errors[TemplateFieldError.PRIORITY_RANGE] = "Priority must be between 1 and 5."
    } else if (recommendedFrom != null && existing.any { other ->
            other.id != draft.id &&
                other.applicableStart == recommendedFrom &&
                other.priority == priority &&
                other.applicableDays.intersect(draft.applicableDays).isNotEmpty()
        }) {
        errors[TemplateFieldError.PRIORITY_CONFLICT] = "This priority conflicts with another template."
    }

    val physical = draft.modules.firstOrNull { it.type == ModuleType.PHYSICAL_WAKE_UP }
    val squats = physical?.behaviours?.firstOrNull { it.key == BehaviourKey.SQUATS }
    if (squats?.mode == BehaviourMode.ON && physical.squatTarget !in 1..30) {
        errors[TemplateFieldError.SQUAT_TARGET_RANGE] = "Squat target must be between 1 and 30."
    }
    val walking = physical?.behaviours?.firstOrNull { it.key == BehaviourKey.WALKING }
    if (walking?.mode == BehaviourMode.ON && physical.stepTarget !in 10..500) {
        errors[TemplateFieldError.WALKING_TARGET_RANGE] = "Walking target must be between 10 and 500."
    }
    return TemplateValidationResult(errors)
}

private val TemplateInputTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** The editor keeps its time as 24-hour text; only what is shown follows the phone's clock. */
fun LocalTime.toTemplateInputText(): String = format(TemplateInputTimeFormatter)

fun parseTemplateTime(value: String): LocalTime? = runCatching {
    LocalTime.parse(value, TemplateInputTimeFormatter)
}.getOrNull()

fun normalizeTemplateModules(modules: List<RoutineModule>): List<RoutineModule> =
    ModuleType.entries.map { type ->
        val module = modules.firstOrNull { it.type == type }
            ?: RoutineModule(type = type, enabled = false)
        if (type == ModuleType.LEAVE_BED) {
            module.copy(enabled = true)
        } else if (!module.enabled || module.behaviours.none { it.enabled }) {
            module.copy(
                enabled = false,
                squatTarget = 0,
                stepTarget = 0,
                behaviours = module.behaviours.map { behaviour ->
                    if (behaviour.key.isSensor) behaviour.copy(mode = BehaviourMode.OFF) else behaviour.copy(mode = BehaviourMode.OFF)
                },
            )
        } else {
            module
        }
    }

fun blankTemplateModules(): List<RoutineModule> = ModuleType.entries.map { type ->
    val behaviours = BehaviourKey.forModule(type).map { key ->
        val mode = when {
            type == ModuleType.LEAVE_BED && key != BehaviourKey.LIGHT_REMINDER -> BehaviourMode.MEDIUM
            else -> BehaviourMode.OFF
        }
        Behaviour(key = key, mode = mode, locked = type == ModuleType.LEAVE_BED)
    }
    RoutineModule(
        type = type,
        enabled = type == ModuleType.LEAVE_BED,
        behaviours = behaviours,
        squatTarget = 0,
        stepTarget = 0,
    )
}

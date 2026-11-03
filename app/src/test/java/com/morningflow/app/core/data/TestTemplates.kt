package com.morningflow.app.core.data

import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.RoutineTemplate
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The richer template set tests exercise recommendation and editing logic against.
 *
 * Production seeds Recommended Morning alone, but choosing between templates — by day,
 * by applicable time, by priority — needs several of them competing, so the development
 * templates that used to ship in the app live on here with their data unchanged.
 */
fun testTemplateSeed(): List<RoutineTemplate> {
    return listOf(
        recommendedTemplate(),
        laterMorningTemplate(),
        sameTimeHighPriorityTemplate(),
        shortSundayTemplate(),
    )
}

private fun laterMorningTemplate() = recommendedTemplate(
    id = "dev-later-morning",
    name = "Later Morning",
).copy(
    applicableStart = LocalTime.of(7, 30),
    priority = 2,
    isDefault = false,
)

private fun sameTimeHighPriorityTemplate() = recommendedTemplate(
    id = "dev-same-time-high-priority",
    name = "Same Time High Priority",
).copy(
    applicableStart = LocalTime.of(7, 30),
    priority = 4,
    isDefault = false,
    modules = standardModules(
        physicalSquatTarget = 0,
        physicalStepTarget = 120,
        modeOverrides = mapOf(
            BehaviourKey.LEAVE_BED to BehaviourMode.LONG,
            BehaviourKey.CHANGE_INTO_HOME_CLOTHES to BehaviourMode.LONG,
            BehaviourKey.LIGHT_REMINDER to BehaviourMode.OFF,
            BehaviourKey.SQUATS to BehaviourMode.OFF,
            BehaviourKey.WALKING to BehaviourMode.ON,
            BehaviourKey.TURN_ON_LIGHT to BehaviourMode.OFF,
            BehaviourKey.MAKE_BED to BehaviourMode.OFF,
            BehaviourKey.WASH_FACE to BehaviourMode.OFF,
            BehaviourKey.MAKEUP to BehaviourMode.OFF,
        ),
        disabledModules = setOf(
            ModuleType.MEAL_PREPARATION,
            ModuleType.BREAKFAST,
        ),
    ),
)

private fun shortSundayTemplate() = recommendedTemplate(
    id = "dev-short-sunday",
    name = "Short Sunday",
).copy(
    applicableDays = setOf(DayOfWeek.SUNDAY),
    applicableStart = LocalTime.of(6, 45),
    priority = 3,
    isDefault = false,
    modules = buildList {
        add(
            moduleOf(
                type = ModuleType.LEAVE_BED,
                modeOverrides = mapOf(BehaviourKey.LIGHT_REMINDER to BehaviourMode.OFF),
            ),
        )
        addAll(
            listOf(
                ModuleType.PHYSICAL_WAKE_UP,
                ModuleType.MORNING_RESET,
                ModuleType.MEAL_PREPARATION,
                ModuleType.WASH_AND_GROOM,
                ModuleType.BREAKFAST,
                ModuleType.GET_READY,
                ModuleType.LEAVING_REMINDER,
            ).map { type -> moduleOf(type, enabled = false) },
        )
    },
)

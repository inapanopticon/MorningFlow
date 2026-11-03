package com.morningflow.app.feature.home

import com.morningflow.app.core.model.GlobalMorningSchedule
import com.morningflow.app.core.model.RoutineTemplate
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** The time state that Home derives before it renders a morning recommendation. */
enum class HomeTimeState {
    NORMAL,
    OFF_WINDOW,
    INSUFFICIENT_TIME,
}

/**
 * The result of applying the Home recommendation rules to the current templates.
 *
 * [selectedTemplateId] and [selectedPlannedSeconds] intentionally keep the precise
 * values needed by the next Home layer. Insufficient Time has no selected template,
 * because Home must not present an automatic recommendation or START in that state.
 */
data class TemplateSelection(
    val timeState: HomeTimeState,
    val selectedTemplateId: String? = null,
    val selectedPlannedSeconds: Int? = null,
    val remainingSeconds: Long? = null,
)

/** Applies the product-defined Home template recommendation rules without persistence. */
object TemplateSelector {
    fun select(
        templates: List<RoutineTemplate>,
        schedule: GlobalMorningSchedule,
        currentDay: DayOfWeek,
        currentTime: LocalTime,
    ): TemplateSelection {
        val defaultTemplate = templates.firstOrNull(RoutineTemplate::isDefault)

        if (!schedule.covers(currentTime)) {
            return defaultTemplate.toSelection(HomeTimeState.OFF_WINDOW)
        }

        val remainingSeconds = Duration.between(currentTime, schedule.readyBy).seconds
        val availableTemplates = templates.filter { template ->
            currentDay in template.applicableDays &&
                !template.applicableStart.isAfter(currentTime)
        }

        val shortestAvailableSeconds = availableTemplates
            .minOfOrNull(RoutineTemplate::plannedSeconds)
            ?: defaultTemplate?.plannedSeconds

        if (shortestAvailableSeconds != null &&
            remainingSeconds < shortestAvailableSeconds + INSUFFICIENT_TIME_BUFFER_SECONDS
        ) {
            return TemplateSelection(
                timeState = HomeTimeState.INSUFFICIENT_TIME,
                remainingSeconds = remainingSeconds,
            )
        }

        val selectedTemplate = availableTemplates
            .sortedWith(TEMPLATE_RECOMMENDATION_COMPARATOR)
            .firstOrNull()
            ?: defaultTemplate

        return selectedTemplate.toSelection(
            timeState = HomeTimeState.NORMAL,
            remainingSeconds = remainingSeconds,
        )
    }

    private fun RoutineTemplate?.toSelection(
        timeState: HomeTimeState,
        remainingSeconds: Long? = null,
    ): TemplateSelection = TemplateSelection(
        timeState = timeState,
        selectedTemplateId = this?.id,
        selectedPlannedSeconds = this?.plannedSeconds,
        remainingSeconds = remainingSeconds,
    )

    private val TEMPLATE_RECOMMENDATION_COMPARATOR = compareByDescending<RoutineTemplate> {
        it.applicableStart
    }.thenByDescending {
        it.priority
    }.thenBy {
        it.id
    }

    private const val INSUFFICIENT_TIME_BUFFER_SECONDS = 5 * 60
}

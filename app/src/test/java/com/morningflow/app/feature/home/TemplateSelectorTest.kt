package com.morningflow.app.feature.home

import com.morningflow.app.core.model.Behaviour
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.GlobalMorningSchedule
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.RoutineModule
import com.morningflow.app.core.model.RoutineTemplate
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateSelectorTest {
    private val schedule = GlobalMorningSchedule(
        earliestWakeUp = LocalTime.of(7, 0),
        latestWakeUp = LocalTime.of(8, 59),
        readyBy = LocalTime.of(9, 0),
    )

    @Test
    fun currentTimeAtWindowBoundariesIsNormal() {
        val template = template(
            id = "normal",
            start = LocalTime.of(7, 0),
            durationMinutes = 20,
        )

        val earliest = select(template, time = LocalTime.of(7, 0))

        assertEquals(HomeTimeState.NORMAL, earliest.timeState)
    }

    @Test
    fun wakeUpWindowEndIsExclusive() {
        val template = template(
            id = "normal",
            start = LocalTime.of(7, 0),
            durationMinutes = 20,
            isDefault = true,
        )

        val atWindowEnd = select(template, time = LocalTime.of(8, 59))
        val afterWindowEnd = select(template, time = LocalTime.of(9, 0))

        assertEquals(HomeTimeState.OFF_WINDOW, atWindowEnd.timeState)
        assertEquals(HomeTimeState.OFF_WINDOW, afterWindowEnd.timeState)
        assertEquals("normal", atWindowEnd.selectedTemplateId)
        assertEquals("normal", afterWindowEnd.selectedTemplateId)
    }

    @Test
    fun currentTimeOutsideWindowUsesDefaultWithoutRecommendationAlgorithm() {
        val defaultTemplate = template(
            id = "default",
            start = LocalTime.of(7, 30),
            priority = 1,
            durationMinutes = 20,
            isDefault = true,
        )
        val higherPriorityTemplate = template(
            id = "higher-priority",
            start = LocalTime.of(7, 0),
            priority = 5,
            durationMinutes = 20,
        )

        val result = select(defaultTemplate, higherPriorityTemplate, time = LocalTime.of(6, 59))

        assertEquals(HomeTimeState.OFF_WINDOW, result.timeState)
        assertEquals("default", result.selectedTemplateId)
        assertEquals(20 * 60, result.selectedPlannedSeconds)
        assertNull(result.remainingSeconds)
    }

    @Test
    fun latestApplicableFromWinsBeforePriority() {
        val earlier = template(
            id = "earlier-high-priority",
            start = LocalTime.of(7, 0),
            priority = 5,
            durationMinutes = 20,
        )
        val later = template(
            id = "later-low-priority",
            start = LocalTime.of(7, 15),
            priority = 1,
            durationMinutes = 20,
        )

        val result = select(earlier, later, time = LocalTime.of(7, 20))

        assertEquals("later-low-priority", result.selectedTemplateId)
    }

    @Test
    fun higherPriorityWinsWhenRecommendedFromMatches() {
        val low = template(id = "low", start = LocalTime.of(7, 0), priority = 2, durationMinutes = 20)
        val high = template(id = "high", start = LocalTime.of(7, 0), priority = 4, durationMinutes = 20)

        val result = select(low, high, time = LocalTime.of(7, 1))

        assertEquals("high", result.selectedTemplateId)
    }

    @Test
    fun templateIdBreaksCompleteRecommendationTieDeterministically() {
        val laterId = template(id = "template-z", start = LocalTime.of(7, 0), priority = 3, durationMinutes = 20)
        val earlierId = template(id = "template-a", start = LocalTime.of(7, 0), priority = 3, durationMinutes = 20)

        val result = select(laterId, earlierId, time = LocalTime.of(7, 1))

        assertEquals("template-a", result.selectedTemplateId)
    }

    @Test
    fun templatesNotApplicableOnCurrentDayAreExcluded() {
        val mondayOnly = template(
            id = "monday",
            start = LocalTime.of(7, 0),
            days = setOf(DayOfWeek.MONDAY),
            durationMinutes = 20,
        )
        val defaultTemplate = template(
            id = "default",
            start = LocalTime.of(7, 0),
            days = setOf(DayOfWeek.TUESDAY),
            durationMinutes = 20,
            isDefault = true,
        )

        val result = select(mondayOnly, defaultTemplate, day = DayOfWeek.TUESDAY, time = LocalTime.of(7, 1))

        assertEquals("default", result.selectedTemplateId)
    }

    @Test
    fun recommendedFromAfterCurrentTimeIsExcluded() {
        val future = template(id = "future", start = LocalTime.of(7, 30), durationMinutes = 20)
        val defaultTemplate = template(
            id = "default",
            start = LocalTime.of(7, 0),
            durationMinutes = 20,
            isDefault = true,
        )

        val result = select(future, defaultTemplate, time = LocalTime.of(7, 15))

        assertEquals("default", result.selectedTemplateId)
    }

    @Test
    fun normalStateUsesDefaultWhenThereAreNoAvailableTemplates() {
        val future = template(id = "future", start = LocalTime.of(7, 30), durationMinutes = 20)
        val defaultTemplate = template(
            id = "default",
            start = LocalTime.of(7, 30),
            durationMinutes = 20,
            isDefault = true,
        )

        val result = select(future, defaultTemplate, time = LocalTime.of(7, 15))

        assertEquals(HomeTimeState.NORMAL, result.timeState)
        assertEquals("default", result.selectedTemplateId)
        assertEquals(20 * 60, result.selectedPlannedSeconds)
    }

    @Test
    fun noAvailableTemplateUsesDefaultForInsufficientTimeCalculation() {
        val defaultTemplate = template(
            id = "default",
            start = LocalTime.of(7, 30),
            days = setOf(DayOfWeek.TUESDAY),
            durationMinutes = 20,
            isDefault = true,
        )

        val result = select(defaultTemplate, time = LocalTime.of(8, 36))

        assertEquals(HomeTimeState.INSUFFICIENT_TIME, result.timeState)
        assertNull(result.selectedTemplateId)
        assertNull(result.selectedPlannedSeconds)
        assertEquals(24 * 60L, result.remainingSeconds)
    }

    @Test
    fun insufficientTimeUsesShortestAvailableTemplateAndFiveMinuteBuffer() {
        val shortTemplate = template(
            id = "short",
            start = LocalTime.of(7, 0),
            priority = 2,
            durationMinutes = 20,
        )
        val longTemplate = template(
            id = "long",
            start = LocalTime.of(7, 0),
            priority = 1,
            durationMinutes = 40,
        )

        val exactlyEnough = select(shortTemplate, longTemplate, time = LocalTime.of(8, 35))
        val tooLate = select(shortTemplate, longTemplate, time = LocalTime.of(8, 36))

        assertEquals(HomeTimeState.NORMAL, exactlyEnough.timeState)
        assertEquals("short", exactlyEnough.selectedTemplateId)
        assertEquals(HomeTimeState.INSUFFICIENT_TIME, tooLate.timeState)
        assertNull(tooLate.selectedTemplateId)
    }

    @Test
    fun insufficientTimeUsesExactNonMinutePlannedSeconds() {
        val walking = walkingTemplate(id = "walking", start = LocalTime.of(7, 0), stepTarget = 1)

        // Walking Planned Duration = 31 + (1 * 0.6), rounded by the existing model to 32 seconds.
        // The threshold is therefore 5 minutes 32 seconds.
        val exactlyEnough = select(walking, time = LocalTime.of(8, 54, 28))
        val oneSecondTooLate = select(walking, time = LocalTime.of(8, 54, 29))

        assertEquals(32, walking.plannedSeconds)
        assertEquals(HomeTimeState.NORMAL, exactlyEnough.timeState)
        assertEquals(HomeTimeState.INSUFFICIENT_TIME, oneSecondTooLate.timeState)
    }

    @Test
    fun recommendedTemplateCanDifferFromShortestTemplateInInsufficientTimeDecision() {
        val shortest = template(
            id = "shortest",
            start = LocalTime.of(7, 0),
            priority = 5,
            durationMinutes = 20,
        )
        val recommended = template(
            id = "recommended",
            start = LocalTime.of(7, 30),
            priority = 1,
            durationMinutes = 40,
        )

        val recommendedWhileTimeAllows = select(shortest, recommended, time = LocalTime.of(7, 31))
        val exactlyAtShortestThreshold = select(shortest, recommended, time = LocalTime.of(8, 35))
        val afterShortestThreshold = select(shortest, recommended, time = LocalTime.of(8, 36))

        assertEquals("recommended", recommendedWhileTimeAllows.selectedTemplateId)
        assertEquals(HomeTimeState.NORMAL, exactlyAtShortestThreshold.timeState)
        assertEquals("recommended", exactlyAtShortestThreshold.selectedTemplateId)
        assertEquals(HomeTimeState.INSUFFICIENT_TIME, afterShortestThreshold.timeState)
        assertNull(afterShortestThreshold.selectedTemplateId)
    }

    @Test
    fun normalSelectionKeepsExactPlannedSecondsAndRemainingSeconds() {
        val selected = template(id = "selected", start = LocalTime.of(7, 0), durationMinutes = 20)

        val result = select(selected, time = LocalTime.of(7, 15))

        assertEquals(HomeTimeState.NORMAL, result.timeState)
        assertEquals("selected", result.selectedTemplateId)
        assertEquals(20 * 60, result.selectedPlannedSeconds)
        assertEquals(105 * 60L, result.remainingSeconds)
    }

    private fun select(
        vararg templates: RoutineTemplate,
        day: DayOfWeek = DayOfWeek.MONDAY,
        time: LocalTime,
    ): TemplateSelection = TemplateSelector.select(
        templates = templates.toList(),
        schedule = schedule,
        currentDay = day,
        currentTime = time,
    )

    private fun template(
        id: String,
        start: LocalTime,
        priority: Int = 1,
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
        durationMinutes: Int,
        isDefault: Boolean = false,
    ) = RoutineTemplate(
        id = id,
        name = id,
        applicableDays = days,
        applicableStart = start,
        priority = priority,
        modules = listOf(
            RoutineModule(
                type = ModuleType.BREAKFAST,
                behaviours = List(durationMinutes / 4) {
                    Behaviour(key = BehaviourKey.BREAKFAST)
                },
            ),
        ),
        isDefault = isDefault,
    )

    private fun walkingTemplate(
        id: String,
        start: LocalTime,
        stepTarget: Int,
    ) = RoutineTemplate(
        id = id,
        name = id,
        applicableDays = setOf(DayOfWeek.MONDAY),
        applicableStart = start,
        modules = listOf(
            RoutineModule(
                type = ModuleType.PHYSICAL_WAKE_UP,
                stepTarget = stepTarget,
                behaviours = listOf(
                    Behaviour(key = BehaviourKey.WALKING),
                ),
            ),
        ),
    )
}

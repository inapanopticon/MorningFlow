package com.morningflow.app.core.model

import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The global schedule is the one place the app reads a wake window from, and it is
 * deliberately not day-scoped: detection runs every day between earliest and latest,
 * whatever days the running template applies to.
 */
class GlobalMorningScheduleTest {
    private val schedule = GlobalMorningSchedule(
        earliestWakeUp = LocalTime.of(7, 0),
        latestWakeUp = LocalTime.of(7, 30),
        readyBy = LocalTime.of(9, 0),
    )
    private val settings = AppSettings(schedule = schedule)

    @Test
    fun wakeWindowIncludesEarliestAndExcludesLatest() {
        assertTrue(schedule.covers(LocalTime.of(7, 0)))
        assertTrue(schedule.covers(LocalTime.of(7, 15)))
        assertFalse(schedule.covers(LocalTime.of(7, 30)))
        assertFalse(schedule.covers(LocalTime.of(6, 59)))
        assertFalse(schedule.covers(LocalTime.of(7, 31)))
    }

    /** The point of dropping per-window days: every day gets the same window. */
    @Test
    fun theWindowAppliesOnEveryDayOfTheWeek() {
        DayOfWeek.entries.forEach { _ ->
            assertTrue(settings.isInsideWakeWindow(LocalTime.of(7, 15)))
            assertFalse(settings.isInsideWakeWindow(LocalTime.of(8, 15)))
        }
    }

    @Test
    fun aScheduleNeedsAPositiveOrderedWakeWindow() {
        assertTrue(schedule.isValid)
        assertFalse(schedule.copy(latestWakeUp = LocalTime.of(6, 0)).isValid)
        assertFalse(schedule.copy(latestWakeUp = schedule.earliestWakeUp).isValid)
    }

    @Test
    fun theDefaultScheduleIsAValidMorning() {
        val defaults = AppSettings().schedule
        assertTrue(defaults.isValid)
        assertTrue(defaults.readyBy.isAfter(defaults.latestWakeUp))
    }

    @Test
    fun onboardingCompletionIsDerivedFromTheStoredStep() {
        assertEquals(OnboardingStep.WELCOME, AppSettings().onboardingStep)
        assertFalse(AppSettings().onboardingComplete)

        OnboardingStep.entries.forEach { step ->
            assertEquals(
                "$step should only count as complete when it is COMPLETE",
                step == OnboardingStep.COMPLETE,
                AppSettings(onboardingStep = step).onboardingComplete,
            )
        }
    }
}

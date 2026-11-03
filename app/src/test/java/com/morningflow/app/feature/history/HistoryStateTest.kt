package com.morningflow.app.feature.history

import com.morningflow.app.core.model.SessionRecord
import com.morningflow.app.core.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class HistoryStateTest {
    private val zone = ZoneId.of("Australia/Melbourne")
    private val today = LocalDate.of(2026, 11, 3)
    private val november = YearMonth.of(2026, 11)

    private fun record(
        date: LocalDate,
        name: String = "Recommended Morning",
        seconds: Int = 1_800,
        status: SessionStatus = SessionStatus.COMPLETED,
        time: LocalTime = LocalTime.of(7, 40),
    ) = SessionRecord(
        templateName = name,
        endedAt = date.atTime(time).atZone(zone).toInstant(),
        actualDurationSeconds = seconds,
        status = status,
        results = emptyList(),
    )

    @Test
    fun finishedDaysAreMarkedInTheirMonthOnly() {
        val state = buildHistoryState(
            listOf(record(today), record(LocalDate.of(2026, 11, 1)), record(LocalDate.of(2026, 10, 31))),
            november, today, zone,
        )
        assertEquals(setOf(today, LocalDate.of(2026, 11, 1)), state.completedDays)
    }

    @Test
    fun sessionsEndedEarlyAreNotCountedAsDone() {
        val state = buildHistoryState(listOf(record(today, status = SessionStatus.EXITED)), november, today, zone)
        assertTrue(state.completedDays.isEmpty())
        assertTrue(state.recent.isEmpty())
        assertFalse(state.hasAnyHistory)
    }

    @Test
    fun recentListIsNewestFirstAndCapped() {
        val records = (1..15).map { record(LocalDate.of(2026, 10, it)) }
        val state = buildHistoryState(records, november, today, zone)
        assertEquals(HistoryUiState.RECENT_LIMIT, state.recent.size)
        assertEquals(LocalDate.of(2026, 10, 15), state.recent.first().date)
    }

    @Test
    fun durationRoundsToTheNearestMinuteAndNeverShowsZero() {
        val state = buildHistoryState(
            listOf(record(today, seconds = 1_650), record(today, seconds = 10, time = LocalTime.of(8, 0))),
            november, today, zone,
        )
        assertEquals(listOf(1, 28), state.recent.map { it.minutes })
    }

    @Test
    fun theDayIsTakenInTheUsersOwnTimeZone() {
        val lateNight = record(LocalDate.of(2026, 11, 2), time = LocalTime.of(23, 30))
        val state = buildHistoryState(listOf(lateNight), november, today, zone)
        assertEquals(LocalDate.of(2026, 11, 2), state.recent.single().date)
    }

    /** November 2026 starts on a Sunday, so the first week has six blanks before it. */
    @Test
    fun weeksStartOnMondayWithBlanksAroundTheMonth() {
        val weeks = buildHistoryState(emptyList(), november, today, zone).weeks
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(6, weeks.first().count { it == null })
        assertEquals(LocalDate.of(2026, 11, 1), weeks.first()[6])
        assertEquals(30, weeks.flatten().count { it != null })
        assertNull(weeks.last().last())
    }

    @Test
    fun theCalendarDoesNotPagePastThisMonth() {
        assertFalse(buildHistoryState(emptyList(), november, today, zone).canShowNextMonth)
        assertTrue(buildHistoryState(emptyList(), november.minusMonths(1), today, zone).canShowNextMonth)
    }
}

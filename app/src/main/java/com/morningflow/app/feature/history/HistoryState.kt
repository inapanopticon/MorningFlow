package com.morningflow.app.feature.history

import com.morningflow.app.core.model.SessionRecord
import com.morningflow.app.core.model.SessionStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One finished morning in the recent list. */
data class HistoryEntry(
    val date: LocalDate,
    val templateName: String,
    val minutes: Int,
)

data class HistoryUiState(
    val month: YearMonth,
    val today: LocalDate,
    /** Days of [month] with at least one finished morning. */
    val completedDays: Set<LocalDate>,
    /** Newest first, at most [RECENT_LIMIT]. */
    val recent: List<HistoryEntry>,
    val hasAnyHistory: Boolean,
) {
    /** The calendar never pages past the current month: there is nothing there yet. */
    val canShowNextMonth: Boolean get() = month < YearMonth.from(today)

    /**
     * The month as rows of seven, Monday first, with null for the blank cells before the
     * first day and after the last.
     */
    val weeks: List<List<LocalDate?>>
        get() {
            val leading = month.atDay(1).dayOfWeek.value - 1
            val cells = List<LocalDate?>(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
            return cells.chunked(7).map { week -> week + List(7 - week.size) { null } }
        }

    companion object {
        const val RECENT_LIMIT = 10
    }
}

/**
 * History is read from saved session records. Only finished mornings count; a session
 * that was ended early is not shown as a completed day.
 */
fun buildHistoryState(
    records: List<SessionRecord>,
    month: YearMonth,
    today: LocalDate,
    zone: ZoneId,
): HistoryUiState {
    val finished = records.filter { it.status == SessionStatus.COMPLETED }
    fun SessionRecord.day(): LocalDate = endedAt.atZone(zone).toLocalDate()
    return HistoryUiState(
        month = month,
        today = today,
        completedDays = finished.map { it.day() }.filter { YearMonth.from(it) == month }.toSet(),
        recent = finished.sortedByDescending { it.endedAt }
            .take(HistoryUiState.RECENT_LIMIT)
            .map { HistoryEntry(it.day(), it.templateName, ((it.actualDurationSeconds + 30) / 60).coerceAtLeast(1)) },
        hasAnyHistory = finished.isNotEmpty(),
    )
}

package com.morningflow.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningflow.app.core.model.SessionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

class HistoryViewModel(
    private val records: StateFlow<List<SessionRecord>>,
    private val clock: Clock = Clock.systemDefaultZone(),
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now(clock))
    private val _uiState = MutableStateFlow(build(records.value, month.value))
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        (scope ?: viewModelScope).launch {
            combine(records, month, ::build).collect { _uiState.value = it }
        }
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (_uiState.value.canShowNextMonth) month.value = month.value.plusMonths(1)
    }

    /** Back to this month, for when the tab is opened again. */
    fun showCurrentMonth() {
        month.value = YearMonth.now(clock)
    }

    private fun build(records: List<SessionRecord>, month: YearMonth) =
        buildHistoryState(records, month, LocalDate.now(clock), clock.zone)
}

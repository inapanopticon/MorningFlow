package com.morningflow.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowBottomNavigation
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowNavigationDestination
import com.morningflow.app.core.designsystem.icon.MorningFlowIcons
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val EntryDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val SpokenDayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

/**
 * The user's finished mornings: this month as a calendar with each finished day marked,
 * and the most recent sessions underneath. Deliberately no scores or streaks.
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onNavigationSelected: (MorningFlowNavigationDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MorningFlowSpacing.Space32),
        ) {
            Spacer(Modifier.height(MorningFlowSpacing.Space32))
            Text(text = stringResource(R.string.app_name), color = colors.textPrimary, style = typography.brandLabel)
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            Text(
                text = stringResource(R.string.history_title),
                modifier = Modifier.fillMaxWidth().semantics { heading() },
                maxLines = 1,
                softWrap = false,
                color = colors.brandPrimary,
                style = typography.pageTitle,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            MonthHeader(state, onPreviousMonth, onNextMonth)
            Spacer(Modifier.height(MorningFlowSpacing.Space12))
            MonthCalendar(state)
            Spacer(Modifier.height(MorningFlowSpacing.Space32))
            Text(
                text = stringResource(R.string.history_recent),
                modifier = Modifier.semantics { heading() },
                color = colors.brandPrimary,
                style = typography.sectionHeading,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space8))
            if (!state.hasAnyHistory) {
                Text(
                    text = stringResource(R.string.history_empty),
                    color = colors.textSecondary,
                    style = typography.body,
                )
            }
            state.recent.forEach { entry ->
                RecentRow(entry)
                HorizontalDivider(color = colors.divider)
            }
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = MorningFlowSpacing.Space8, bottom = MorningFlowSpacing.Space12),
            contentAlignment = Alignment.Center,
        ) {
            MorningFlowBottomNavigation(
                selected = MorningFlowNavigationDestination.History,
                onSelected = onNavigationSelected,
            )
        }
    }
}

@Composable
private fun MonthHeader(state: HistoryUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    val colors = MorningFlowDesignSystem.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = state.month.format(MonthFormatter),
            modifier = Modifier.weight(1f),
            color = colors.textPrimary,
            style = MorningFlowDesignSystem.typography.sectionHeading,
        )
        IconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(MorningFlowIcons.ChevronLeft),
                contentDescription = stringResource(R.string.history_previous_month),
                modifier = Modifier.size(14.dp),
                tint = colors.textPrimary,
            )
        }
        IconButton(onClick = onNext, enabled = state.canShowNextMonth) {
            Icon(
                painter = painterResource(MorningFlowIcons.ChevronLeft),
                contentDescription = stringResource(R.string.history_next_month),
                modifier = Modifier
                    .size(14.dp)
                    .rotate(180f)
                    .alpha(if (state.canShowNextMonth) 1f else 0.3f),
                tint = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun MonthCalendar(state: HistoryUiState) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    Column(verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space4)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    modifier = Modifier.weight(1f),
                    color = colors.textSecondary,
                    style = typography.smallRegular,
                    textAlign = TextAlign.Center,
                )
            }
        }
        state.weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(date, done = date in state.completedDays, today = date == state.today, future = date.isAfter(state.today))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, done: Boolean, today: Boolean, future: Boolean) {
    val colors = MorningFlowDesignSystem.colors
    val day = date.format(SpokenDayFormatter)
    val spoken = when {
        done -> stringResource(R.string.history_day_done, day)
        today -> stringResource(R.string.history_day_today, day)
        else -> day
    }
    Box(
        modifier = Modifier
            .clearAndSetSemantics { contentDescription = spoken }
            .size(34.dp)
            .clip(CircleShape)
            .background(if (done) colors.brandPrimary else Color.Transparent)
            .then(if (today && !done) Modifier.border(1.dp, colors.brandPrimary, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = when {
                done -> colors.surface
                future -> colors.borderInput
                else -> colors.textPrimary
            },
            style = MorningFlowDesignSystem.typography.smallStrong,
        )
    }
}

@Composable
private fun RecentRow(entry: HistoryEntry) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.date.format(EntryDateFormatter), color = colors.textPrimary, style = typography.fieldValue)
            Text(text = entry.templateName, color = colors.textSecondary, style = typography.smallRegular)
        }
        Text(
            text = stringResource(R.string.completion_duration_value, entry.minutes),
            color = colors.textPrimary,
            style = typography.body,
        )
    }
}

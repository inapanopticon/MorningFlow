package com.morningflow.app.feature.session.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.designsystem.theme.illustrationFrame
import com.morningflow.app.feature.session.LeavingReminderItemDisplayData
import com.morningflow.app.feature.session.LeavingReminderPresenter
import com.morningflow.app.feature.session.LeavingReminderScheduleItemDisplayData
import com.morningflow.app.feature.session.MorningSessionDisplayData
import com.morningflow.app.feature.session.SessionDisplayText

@Composable
fun LeavingReminderContent(
    data: MorningSessionDisplayData,
    modifier: Modifier = Modifier,
) {
    val leavingReminder = data.leavingReminder ?: LeavingReminderPresenter.loading()
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    val hasReminders = leavingReminder.reminders.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 28.dp, end = 28.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.session_leaving_intro),
            color = colors.brandPrimary,
            style = typography.reminderIntro,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = leavingReminder.locationWeather.resolveText(),
            color = if (hasReminders) colors.textPrimary else colors.textSecondary,
            style = if (hasReminders) typography.reminderLocation else typography.smallStrong,
        )

        if (hasReminders) {
            Spacer(Modifier.height(18.dp))
            LeavingReminderSectionLabel(stringResource(R.string.session_reminders_label))
            Spacer(Modifier.height(MorningFlowSpacing.Space12))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                leavingReminder.reminders.forEach { item ->
                    LeavingReminderItem(item)
                }
            }
            Spacer(Modifier.height(22.dp))
        } else {
            Spacer(Modifier.height(MorningFlowSpacing.Space16))
        }

        LeavingReminderDivider()
        Spacer(Modifier.height(MorningFlowSpacing.Space16))
        LeavingReminderSectionLabel(stringResource(R.string.session_take_with_you_label))
        Spacer(Modifier.height(MorningFlowSpacing.Space12))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
            verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
        ) {
            leavingReminder.thingsToBring.forEach { item ->
                ThingsToBringChip(item)
            }
        }
        Spacer(Modifier.height(15.dp))
        LeavingReminderDivider()
        Spacer(Modifier.height(MorningFlowSpacing.Space16))
        LeavingReminderSectionLabel(stringResource(R.string.session_coming_next_label))
        Spacer(Modifier.height(MorningFlowSpacing.Space12))
        Column(verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12)) {
            leavingReminder.scheduleItems.forEach { item ->
                ScheduleItem(item)
            }
        }
        Spacer(Modifier.height(MorningFlowSpacing.Space32))
        Image(
            painter = painterResource(data.illustration),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(362.dp).illustrationFrame(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
internal fun LeavingReminderItem(data: LeavingReminderItemDisplayData) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(data.icon),
            contentDescription = null,
            modifier = Modifier.size(MorningFlowSpacing.Space24),
            tint = Color.Unspecified,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space6),
        ) {
            Text(
                text = data.title.resolveText(),
                modifier = Modifier.fillMaxWidth(),
                color = colors.textPrimary,
                style = typography.sectionHeading,
            )
            Text(
                text = data.description.resolveText(),
                modifier = Modifier.fillMaxWidth(),
                color = colors.textSecondary,
                style = typography.reminderDescription,
            )
        }
    }
}

@Composable
internal fun ThingsToBringChip(label: SessionDisplayText) {
    val colors = MorningFlowDesignSystem.colors
    Text(
        text = label.resolveText(),
        modifier = Modifier
            .border(1.dp, colors.divider, MorningFlowShapes.Full)
            .padding(horizontal = MorningFlowSpacing.Space16, vertical = MorningFlowSpacing.Space8),
        color = colors.textPrimary,
        style = MorningFlowDesignSystem.typography.smallStrong,
    )
}

@Composable
internal fun ScheduleItem(data: LeavingReminderScheduleItemDisplayData) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography

    if (data.time == null) {
        Text(
            text = data.title.resolveText(),
            modifier = Modifier.padding(start = 7.dp),
            color = colors.textSecondary,
            style = typography.smallStrong,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.time,
                modifier = Modifier.width(91.dp),
                color = colors.textPrimary,
                style = typography.smallStrong,
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(20.dp)
                    .background(colors.divider),
            )
            Text(
                text = data.title.resolveText(),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MorningFlowSpacing.Space16),
                color = colors.textPrimary,
                style = typography.smallRegular,
            )
        }
    }
}

@Composable
internal fun LeavingReminderSectionLabel(label: String) {
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth(),
        color = MorningFlowDesignSystem.colors.brandPrimary,
        style = MorningFlowDesignSystem.typography.smallStrong,
    )
}

@Composable
private fun LeavingReminderDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = MorningFlowDesignSystem.colors.divider,
    )
}

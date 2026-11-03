package com.morningflow.app.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.common.PrimaryButton
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.feature.home.HomeDisplayData

@Composable
fun HomeRoutineSection(
    data: HomeDisplayData,
    onStart: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography

    Column(modifier = modifier.fillMaxWidth()) {
        if (onStart != null) {
            Spacer(Modifier.height(MorningFlowSpacing.Space16))
            PrimaryButton(
                label = stringResource(R.string.action_start),
                onClick = onStart,
            )
            // Room between the call to action and the list, so neither crowds the other.
            Spacer(Modifier.height(MorningFlowSpacing.Space32))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.templateTitle,
                color = colors.textPrimary,
                style = typography.smallStrong,
            )
            Text(
                text = stringResource(R.string.home_minutes_short, data.totalDurationMinutes),
                color = colors.textPrimary,
                style = typography.smallStrong,
            )
        }
        Spacer(Modifier.height(MorningFlowSpacing.Space8))
        data.modules.forEachIndexed { index, module ->
            HomeRoutineModuleRow(
                sequence = "%02d".format(index + 1),
                title = module.title,
                duration = stringResource(R.string.home_minutes_short, module.durationMinutes),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomeRoutineModuleRow(
    sequence: String,
    title: String,
    duration: String,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sequence,
                color = colors.textPrimary,
                style = MorningFlowDesignSystem.typography.smallStrong,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.widthIn(min = 20.dp),
            )
            Spacer(modifier = Modifier.width(MorningFlowSpacing.Space24))
            Text(
                text = title,
                color = colors.textPrimary,
                style = MorningFlowDesignSystem.typography.moduleTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = duration,
            color = colors.textSecondary,
            style = MorningFlowDesignSystem.typography.smallRegular,
            maxLines = 1,
            softWrap = false,
        )
    }
}

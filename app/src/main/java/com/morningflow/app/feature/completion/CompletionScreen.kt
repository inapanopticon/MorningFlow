package com.morningflow.app.feature.completion

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.common.PrimaryButton
import com.morningflow.app.core.designsystem.component.navigation.EditorialArrowButton
import com.morningflow.app.core.designsystem.component.session.SessionStageTopBar
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.designsystem.theme.illustrationFrame
import com.morningflow.app.core.designsystem.time.ClockFormat
import java.time.format.DateTimeFormatter

@Composable
fun CompletionScreen(
    data: CompletionDisplayData,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Spacer(Modifier.height(MorningFlowSpacing.Space32))
        SessionStageTopBar(
            step = stringResource(R.string.app_name),
            title = stringResource(R.string.completion_header),
            modifier = Modifier.padding(horizontal = MorningFlowSpacing.Space32),
        )
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            // The artwork shrinks first on short screens so the button never covers the summary.
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(data.illustration),
                    contentDescription = null,
                    modifier = Modifier
                        .widthIn(max = 390.dp)
                        .aspectRatio(1f, matchHeightConstraintsFirst = true).illustrationFrame(),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            Text(
                text = stringResource(R.string.completion_title),
                modifier = Modifier.fillMaxWidth().padding(horizontal = MorningFlowSpacing.Space32).semantics { heading() },
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                style = typography.completionTitle,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space8))
            Text(
                text = stringResource(R.string.completion_message),
                modifier = Modifier.fillMaxWidth().padding(horizontal = MorningFlowSpacing.Space32),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                style = typography.body,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = MorningFlowSpacing.Space32),
                horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
            ) {
                CompletionStat(
                    label = stringResource(R.string.completion_stat_duration),
                    value = stringResource(R.string.completion_duration_value, data.actualDurationMinutes),
                    modifier = Modifier.weight(1f),
                )
                CompletionStat(
                    label = stringResource(R.string.completion_stat_finished),
                    value = ClockFormat.format(data.finishedAt),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        PrimaryButton(
            label = stringResource(R.string.completion_home),
            onClick = onHome,
            modifier = Modifier.padding(horizontal = MorningFlowSpacing.Space32, vertical = MorningFlowSpacing.Space32),
        )
    }
}

@Composable
private fun CompletionStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MorningFlowDesignSystem.colors
    Column(
        modifier = modifier
            .clip(MorningFlowShapes.Radius16)
            .background(colors.surface)
            .padding(vertical = MorningFlowSpacing.Space16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, color = colors.textPrimary, style = MorningFlowDesignSystem.typography.moduleTitle)
        Text(text = label, color = colors.textSecondary, style = MorningFlowDesignSystem.typography.smallRegular)
    }
}

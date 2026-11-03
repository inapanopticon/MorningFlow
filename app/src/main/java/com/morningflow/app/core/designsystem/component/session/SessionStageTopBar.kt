package com.morningflow.app.core.designsystem.component.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.component.ComponentPreviewData
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

@Composable
fun SessionStageTopBar(
    step: String,
    title: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val colors = MorningFlowDesignSystem.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 21.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = step, color = colors.textPrimary, style = MorningFlowDesignSystem.typography.brandLabel)
            Text(
                text = title,
                color = colors.textPrimary,
                style = MorningFlowDesignSystem.typography.brandLabel,
                textAlign = TextAlign.End,
            )
        }
        if (progress != null) {
            val animated by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 600),
                label = "sessionProgress",
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space8))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = colors.brandPrimary,
                trackColor = colors.divider,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Preview(name = "Session Stage Top Bar", showBackground = true)
@Composable
private fun SessionStageTopBarPreview() {
    MorningFlowComponentPreview {
        SessionStageTopBar(ComponentPreviewData.SessionStep, ComponentPreviewData.SessionTitle)
    }
}

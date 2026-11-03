package com.morningflow.app.core.designsystem.component.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.component.ComponentPreviewData
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

@Composable
fun TemplateStageRow(
    sequence: String,
    title: String,
    duration: String,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    Row(
        modifier = modifier.width(326.dp).height(40.5.dp),
        horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            Text(text = sequence, color = colors.textPrimary, style = MorningFlowDesignSystem.typography.stageTitle)
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            Text(text = title, color = colors.textPrimary, style = MorningFlowDesignSystem.typography.stageTitle)
        }
        Box(modifier = Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = duration,
                color = colors.textSecondary,
                style = MorningFlowDesignSystem.typography.stageDuration,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Preview(name = "Template Stage Row", showBackground = true)
@Composable
private fun TemplateStageRowPreview() {
    MorningFlowComponentPreview {
        TemplateStageRow(
            ComponentPreviewData.Sequence,
            ComponentPreviewData.StageTitle,
            ComponentPreviewData.StageDuration,
        )
    }
}

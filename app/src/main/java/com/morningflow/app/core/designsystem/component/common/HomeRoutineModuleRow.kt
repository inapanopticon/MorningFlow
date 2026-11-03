package com.morningflow.app.core.designsystem.component.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.component.ComponentPreviewData
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

@Composable
fun HomeRoutineModuleRow(
    sequence: String,
    title: String,
    duration: String,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    Box(modifier = modifier.width(394.dp).height(44.dp)) {
        Row(modifier = Modifier.offset(y = 17.dp)) {
            Box(modifier = Modifier.width(20.dp).height(19.5.dp)) {
                Text(
                    text = sequence,
                    color = colors.textPrimary,
                    style = MorningFlowDesignSystem.typography.smallStrong,
                )
            }
            Spacer(modifier = Modifier.width(MorningFlowSpacing.Space24))
            Text(
                text = title,
                color = colors.textPrimary,
                style = MorningFlowDesignSystem.typography.moduleTitle,
            )
        }
        Text(
            text = duration,
            modifier = Modifier.offset(x = 302.25.dp, y = 23.dp),
            color = colors.textSecondary,
            style = MorningFlowDesignSystem.typography.smallRegular,
        )
    }
}

@Preview(name = "Home Routine Module Row", showBackground = true, widthDp = 420)
@Composable
private fun HomeRoutineModuleRowPreview() {
    MorningFlowComponentPreview {
        HomeRoutineModuleRow(
            sequence = ComponentPreviewData.Sequence,
            title = ComponentPreviewData.RoutineTitle,
            duration = ComponentPreviewData.RoutineDuration,
        )
    }
}

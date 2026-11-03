package com.morningflow.app.core.designsystem.component.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.component.ComponentPreviewData
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

enum class AvailabilityType { Voice, StepSensor, LightSensor }

@Composable
fun AvailabilityBadge(
    type: AvailabilityType,
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    Box(
        modifier = modifier.height(20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(bottom = 1.dp)
                .align(Alignment.TopStart)
                .background(colors.foregroundEmphasis),
        )
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = MorningFlowSpacing.Space5),
            color = colors.warning,
            style = MorningFlowDesignSystem.typography.statusBadge,
        )
    }
}

@Preview(name = "Availability Badge Variants", showBackground = true)
@Composable
private fun AvailabilityBadgePreview() {
    MorningFlowComponentPreview {
        AvailabilityBadge(AvailabilityType.Voice, ComponentPreviewData.VoiceUnavailable)
        AvailabilityBadge(AvailabilityType.StepSensor, ComponentPreviewData.StepSensorUnavailable)
        AvailabilityBadge(AvailabilityType.LightSensor, ComponentPreviewData.LightSensorUnavailable)
    }
}

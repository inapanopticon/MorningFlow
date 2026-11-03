package com.morningflow.app.core.designsystem.component.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.component.ComponentPreviewData
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

@Composable
fun DayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val containerModifier = modifier
        .height(31.dp)
        .clip(MorningFlowShapes.Radius20)
        .background(if (selected) colors.textPrimary else colors.background)
        .then(
            if (selected) Modifier else Modifier.border(1.dp, colors.borderDefault, MorningFlowShapes.Radius20),
        )
        .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
        .padding(horizontal = MorningFlowSpacing.Space12)

    Box(modifier = containerModifier, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = if (selected) colors.background else colors.textSecondary,
            style = MorningFlowDesignSystem.typography.dayChipLabel,
        )
    }
}

@Preview(name = "Day Chip Variants", showBackground = true)
@Composable
private fun DayChipPreview() {
    MorningFlowComponentPreview {
        DayChip(ComponentPreviewData.Day, selected = true, onClick = {})
        DayChip(ComponentPreviewData.Day, selected = false, onClick = {})
    }
}

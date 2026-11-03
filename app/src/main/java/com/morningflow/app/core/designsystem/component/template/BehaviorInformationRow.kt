package com.morningflow.app.core.designsystem.component.template

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.component.ComponentPreviewData
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

@Composable
fun BehaviorInformationRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    options: List<String> = emptyList(),
    onOptionSelected: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(end = MorningFlowSpacing.Space8),
            color = colors.textPrimary,
            style = MorningFlowDesignSystem.typography.body,
        )
        Box {
            Box(
                modifier = Modifier
                    .widthIn(min = 93.dp)
                    .heightIn(min = 32.dp)
                    .border(1.dp, colors.borderDefault, MorningFlowShapes.Radius16)
                    .then(if (options.isNotEmpty()) Modifier.clickable { expanded = true } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value,
                    modifier = Modifier.padding(horizontal = MorningFlowSpacing.Space12),
                    color = colors.textPrimary,
                    style = MorningFlowDesignSystem.typography.body,
                    maxLines = 1,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onOptionSelected?.invoke(option)
                        },
                    )
                }
            }
        }
    }
}

@Preview(name = "Behavior Information Row", showBackground = true)
@Composable
private fun BehaviorInformationRowPreview() {
    MorningFlowComponentPreview {
        BehaviorInformationRow(
            label = ComponentPreviewData.BehaviorLabel,
            value = ComponentPreviewData.BehaviorValue,
        )
    }
}

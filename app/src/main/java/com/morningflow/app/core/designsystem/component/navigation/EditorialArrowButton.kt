package com.morningflow.app.core.designsystem.component.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.component.ComponentPreviewData
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.icon.MorningFlowIcons
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

@Composable
fun EditorialArrowButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 97.dp)
            .height(32.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            color = colors.brandPrimary,
            style = MorningFlowDesignSystem.typography.primaryCta,
        )
        Row(
            modifier = Modifier
                .size(width = 24.dp, height = 16.dp)
                .padding(start = MorningFlowSpacing.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(MorningFlowIcons.EditorialArrow),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.Unspecified,
            )
        }
    }
}

@Preview(name = "Editorial Arrow Button", showBackground = true)
@Composable
private fun EditorialArrowButtonPreview() {
    MorningFlowComponentPreview {
        EditorialArrowButton(label = ComponentPreviewData.PrimaryAction, onClick = {})
    }
}

package com.morningflow.app.core.designsystem.component.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.ComponentPreviewData
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.icon.MorningFlowIcons
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

@Composable
fun EditorTopBar(
    title: String,
    action: String,
    onBack: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = MorningFlowSpacing.Space24),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onBack),
            horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(MorningFlowIcons.ChevronLeft),
                contentDescription = stringResource(R.string.content_description_back),
                modifier = Modifier.size(16.dp),
                tint = colors.textPrimary,
            )
            Text(text = title, color = colors.textPrimary, style = MorningFlowDesignSystem.typography.actionLabel)
        }
        Text(
            text = action,
            modifier = Modifier.clickable(onClick = onAction),
            color = colors.brandPrimary,
            style = MorningFlowDesignSystem.typography.actionLabel,
        )
    }
}

@Preview(name = "Editor Top Bar", showBackground = true, widthDp = 390)
@Composable
private fun EditorTopBarPreview() {
    MorningFlowComponentPreview {
        EditorTopBar(
            title = ComponentPreviewData.BackTitle,
            action = ComponentPreviewData.Action,
            onBack = {},
            onAction = {},
        )
    }
}

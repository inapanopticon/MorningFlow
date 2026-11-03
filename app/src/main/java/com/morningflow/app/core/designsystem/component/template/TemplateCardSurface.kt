package com.morningflow.app.core.designsystem.component.template

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowElevation
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

@Composable
fun TemplateCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MorningFlowDesignSystem.colors
    val shadow = MorningFlowElevation.Card

    Column(
        modifier = modifier
            .shadow(
                elevation = shadow.yOffset,
                shape = MorningFlowShapes.Radius20,
                ambientColor = shadow.color,
                spotColor = shadow.color,
            )
            .background(colors.surface, MorningFlowShapes.Radius20)
            .padding(MorningFlowSpacing.Space16),
        verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
        content = content,
    )
}

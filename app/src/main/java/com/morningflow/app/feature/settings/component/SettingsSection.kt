package com.morningflow.app.feature.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space6),
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            color = colors.brandPrimary,
            style = MorningFlowDesignSystem.typography.sectionHeading,
        )
    }
}

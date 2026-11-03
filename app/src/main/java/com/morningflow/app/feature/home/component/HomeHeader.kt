package com.morningflow.app.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem

@Composable
fun HomeHeader(
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val style = MorningFlowDesignSystem.typography.brandLabel

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The status bar already shows the date and time, so the header carries only the name.
        Text(text = stringResource(R.string.app_name), color = colors.textPrimary, style = style)
    }
}

package com.morningflow.app.feature.session.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.morningflow.app.feature.session.MorningSessionDisplayData

@Composable
fun PhysicalWakeUpContent(
    data: MorningSessionDisplayData,
    modifier: Modifier = Modifier,
) {
    SessionGuidanceContent(data = data, modifier = modifier)
}

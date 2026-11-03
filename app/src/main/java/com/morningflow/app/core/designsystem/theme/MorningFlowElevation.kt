package com.morningflow.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class MorningFlowShadow(
    val xOffset: Dp,
    val yOffset: Dp,
    val blurRadius: Dp,
    val spreadRadius: Dp,
    val color: Color,
)

object MorningFlowElevation {
    val Dialog = MorningFlowShadow(
        xOffset = 0.dp,
        yOffset = 18.dp,
        blurRadius = 48.dp,
        spreadRadius = (-8).dp,
        color = MorningFlowColorTokens.ForegroundEmphasis.copy(alpha = 0.22f),
    )

    val Card = MorningFlowShadow(
        xOffset = 0.dp,
        yOffset = 2.dp,
        blurRadius = 4.dp,
        spreadRadius = 0.dp,
        color = Color.Black.copy(alpha = 0.06f),
    )
}

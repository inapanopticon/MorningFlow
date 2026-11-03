package com.morningflow.app.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

object MorningFlowColorTokens {
    val TextPrimary = Color(0xFF141414)
    val TextSecondary = Color(0xFF8E8C87)
    val BrandPrimary = Color(0xFF016D4D)
    val Background = Color(0xFFFBF9F4)
    val NavSurface = Color(0xFF161614)
    val NavItemActive = Color(0xFF3A3A36)
    val NavItemInactive = Color(0xFF787672)
    val ForegroundEmphasis = Color(0xFF181314)
    val Warning = Color(0xFFE5470E)
    val BorderInput = Color(0xFFD8D4CC)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceElevated = Color(0xFFFFFDF9)
    val BorderSubtle = Color(0xFFDED9D0)
    val BorderDefault = Color(0xFFD9D9D9)
    val SwitchActive = Color(0xFF26784D)
    val Divider = Color(0xFFEEEBE4)
}

/** Night palette: warm near-black paper with the same green, lifted for contrast. */
object MorningFlowDarkColorTokens {
    val TextPrimary = Color(0xFFEDEAE3)
    val TextSecondary = Color(0xFFA29F98)
    val BrandPrimary = Color(0xFF5CC497)
    val Background = Color(0xFF141412)
    val NavSurface = Color(0xFF2A2A27)
    val NavItemActive = Color(0xFFEDEAE3)
    val NavItemInactive = Color(0xFF8E8B85)
    val ForegroundEmphasis = Color(0xFFF3F0EA)
    val Warning = Color(0xFFFF8152)
    val BorderInput = Color(0xFF4A4843)
    val Surface = Color(0xFF1F1F1C)
    val SurfaceElevated = Color(0xFF262623)
    val BorderSubtle = Color(0xFF3A3935)
    val BorderDefault = Color(0xFF45433F)
    val SwitchActive = Color(0xFF5CC497)
    val Divider = Color(0xFF2E2D2A)
    val IllustrationBackdrop = Color(0xFFF1ECE2)
}

@Immutable
data class MorningFlowColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val brandPrimary: Color,
    val background: Color,
    val navSurface: Color,
    val navItemActive: Color,
    val navItemInactive: Color,
    val foregroundEmphasis: Color,
    val warning: Color,
    val borderInput: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val borderSubtle: Color,
    val borderDefault: Color,
    val switchActive: Color,
    val divider: Color,
    /**
     * Drawn behind illustrations. The artwork is inked in black for light paper, so on the
     * dark theme it sits on a light card; on the light theme there is nothing behind it.
     */
    val illustrationBackdrop: Color = Color.Transparent,
    val isDark: Boolean = false,
)

internal val MorningFlowLightColors = MorningFlowColors(
    textPrimary = MorningFlowColorTokens.TextPrimary,
    textSecondary = MorningFlowColorTokens.TextSecondary,
    brandPrimary = MorningFlowColorTokens.BrandPrimary,
    background = MorningFlowColorTokens.Background,
    navSurface = MorningFlowColorTokens.NavSurface,
    navItemActive = MorningFlowColorTokens.NavItemActive,
    navItemInactive = MorningFlowColorTokens.NavItemInactive,
    foregroundEmphasis = MorningFlowColorTokens.ForegroundEmphasis,
    warning = MorningFlowColorTokens.Warning,
    borderInput = MorningFlowColorTokens.BorderInput,
    surface = MorningFlowColorTokens.Surface,
    surfaceElevated = MorningFlowColorTokens.SurfaceElevated,
    borderSubtle = MorningFlowColorTokens.BorderSubtle,
    borderDefault = MorningFlowColorTokens.BorderDefault,
    switchActive = MorningFlowColorTokens.SwitchActive,
    divider = MorningFlowColorTokens.Divider,
)

internal val MorningFlowDarkColors = MorningFlowColors(
    textPrimary = MorningFlowDarkColorTokens.TextPrimary,
    textSecondary = MorningFlowDarkColorTokens.TextSecondary,
    brandPrimary = MorningFlowDarkColorTokens.BrandPrimary,
    background = MorningFlowDarkColorTokens.Background,
    navSurface = MorningFlowDarkColorTokens.NavSurface,
    navItemActive = MorningFlowDarkColorTokens.NavItemActive,
    navItemInactive = MorningFlowDarkColorTokens.NavItemInactive,
    foregroundEmphasis = MorningFlowDarkColorTokens.ForegroundEmphasis,
    warning = MorningFlowDarkColorTokens.Warning,
    borderInput = MorningFlowDarkColorTokens.BorderInput,
    surface = MorningFlowDarkColorTokens.Surface,
    surfaceElevated = MorningFlowDarkColorTokens.SurfaceElevated,
    borderSubtle = MorningFlowDarkColorTokens.BorderSubtle,
    borderDefault = MorningFlowDarkColorTokens.BorderDefault,
    switchActive = MorningFlowDarkColorTokens.SwitchActive,
    divider = MorningFlowDarkColorTokens.Divider,
    illustrationBackdrop = MorningFlowDarkColorTokens.IllustrationBackdrop,
    isDark = true,
)

/** Frames an illustration so it stays legible on the dark theme; a no-op on the light one. */
fun Modifier.illustrationFrame(): Modifier = composed {
    val backdrop = MorningFlowDesignSystem.colors.illustrationBackdrop
    if (backdrop == Color.Transparent) this
    else this.clip(MorningFlowShapes.Radius24).background(backdrop).padding(MorningFlowSpacing.Space8)
}


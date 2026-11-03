package com.morningflow.app.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val LocalMorningFlowColors = staticCompositionLocalOf { MorningFlowLightColors }
private val LocalMorningFlowTypography = staticCompositionLocalOf { MorningFlowTypographyTokens }

object MorningFlowDesignSystem {
    val colors: MorningFlowColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMorningFlowColors.current

    val typography: MorningFlowTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMorningFlowTypography.current
}

private val MorningFlowLightColorScheme = lightColorScheme(
    primary = MorningFlowColorTokens.BrandPrimary,
    onPrimary = MorningFlowColorTokens.Surface,
    background = MorningFlowColorTokens.Background,
    onBackground = MorningFlowColorTokens.TextPrimary,
    surface = MorningFlowColorTokens.Surface,
    onSurface = MorningFlowColorTokens.TextPrimary,
    surfaceVariant = MorningFlowColorTokens.SurfaceElevated,
    onSurfaceVariant = MorningFlowColorTokens.TextSecondary,
    outline = MorningFlowColorTokens.BorderDefault,
    outlineVariant = MorningFlowColorTokens.BorderSubtle,
    error = MorningFlowColorTokens.Warning,
    onError = MorningFlowColorTokens.Surface,
)

private val MorningFlowDarkColorScheme = darkColorScheme(
    primary = MorningFlowDarkColorTokens.BrandPrimary,
    onPrimary = MorningFlowDarkColorTokens.Surface,
    background = MorningFlowDarkColorTokens.Background,
    onBackground = MorningFlowDarkColorTokens.TextPrimary,
    surface = MorningFlowDarkColorTokens.Surface,
    onSurface = MorningFlowDarkColorTokens.TextPrimary,
    surfaceVariant = MorningFlowDarkColorTokens.SurfaceElevated,
    onSurfaceVariant = MorningFlowDarkColorTokens.TextSecondary,
    outline = MorningFlowDarkColorTokens.BorderDefault,
    outlineVariant = MorningFlowDarkColorTokens.BorderSubtle,
    error = MorningFlowDarkColorTokens.Warning,
    onError = MorningFlowDarkColorTokens.Surface,
)

private val MorningFlowMaterialTypography = Typography(
    displayLarge = MorningFlowTypographyTokens.pageTitle,
    displayMedium = MorningFlowTypographyTokens.timeDisplay,
    headlineMedium = MorningFlowTypographyTokens.dialogTitle,
    titleLarge = MorningFlowTypographyTokens.moduleTitle,
    titleMedium = MorningFlowTypographyTokens.sectionHeading,
    bodyMedium = MorningFlowTypographyTokens.body,
    labelLarge = MorningFlowTypographyTokens.buttonLabel,
    labelMedium = MorningFlowTypographyTokens.actionLabel,
    labelSmall = MorningFlowTypographyTokens.bottomNavigationLabel,
)

@Composable
fun MorningFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMorningFlowColors provides if (darkTheme) MorningFlowDarkColors else MorningFlowLightColors,
        LocalMorningFlowTypography provides MorningFlowTypographyTokens,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) MorningFlowDarkColorScheme else MorningFlowLightColorScheme,
            typography = MorningFlowMaterialTypography,
            shapes = androidx.compose.material3.Shapes(
                small = MorningFlowShapes.Radius8,
                medium = MorningFlowShapes.Radius14,
                large = MorningFlowShapes.Radius20,
            ),
            content = content,
        )
    }
}

@Composable
private fun ColorTokenRow(name: String, color: Color) {
    val colors = MorningFlowDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .border(1.dp, colors.borderSubtle, MorningFlowShapes.Radius8),
            color = color,
            shape = MorningFlowShapes.Radius8,
            content = {},
        )
        Text(
            text = name,
            color = colors.textPrimary,
            style = MorningFlowDesignSystem.typography.body,
        )
    }
}

@Composable
private fun TypeSample(label: String, sample: String, style: TextStyle) {
    val colors = MorningFlowDesignSystem.colors
    Column(verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space4)) {
        Text(
            text = label,
            color = colors.textSecondary,
            style = MorningFlowDesignSystem.typography.actionLabel,
        )
        Text(
            text = sample,
            color = colors.textPrimary,
            style = style,
        )
    }
}

@Preview(
    name = "MorningFlow Light Theme",
    showBackground = true,
    backgroundColor = 0xFFFBF9F4,
    widthDp = 390,
    heightDp = 1000,
)
@Composable
private fun MorningFlowThemePreview() {
    MorningFlowTheme {
        val colors = MorningFlowDesignSystem.colors
        val typography = MorningFlowDesignSystem.typography

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(MorningFlowSpacing.Space24),
            verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space16),
        ) {
            Text(
                text = "MorningFlow Theme",
                color = colors.brandPrimary,
                style = typography.pageTitle,
            )

            TypeSample("PAGE TITLE", "Start your morning", typography.pageTitle)
            TypeSample("DIALOG TITLE", "End your morning session?", typography.dialogTitle)
            TypeSample("MODULE TITLE", "Get out of bed", typography.moduleTitle)
            TypeSample("SECTION HEADING", "Recommended Morning", typography.sectionHeading)
            TypeSample("BODY", "A calm, focused start to the day.", typography.body)
            TypeSample("ACTION LABEL", "CONTINUE", typography.actionLabel)

            Text(
                text = "Semantic colors",
                color = colors.textPrimary,
                style = typography.sectionHeading,
            )
            ColorTokenRow("Background", colors.background)
            ColorTokenRow("Brand primary", colors.brandPrimary)
            ColorTokenRow("Text primary", colors.textPrimary)
            ColorTokenRow("Text secondary", colors.textSecondary)
            ColorTokenRow("Navigation surface", colors.navSurface)
            ColorTokenRow("Warning", colors.warning)
            ColorTokenRow("Border subtle", colors.borderSubtle)
        }
    }
}

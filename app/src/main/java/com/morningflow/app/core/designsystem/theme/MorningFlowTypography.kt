package com.morningflow.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.morningflow.app.R

internal val MorningFlowFontFamily = FontFamily(
    Font(
        resId = R.font.montserrat_regular,
        weight = FontWeight.Normal,
        style = FontStyle.Normal,
    ),
    Font(
        resId = R.font.montserrat_italic,
        weight = FontWeight.Normal,
        style = FontStyle.Italic,
    ),
    Font(
        resId = R.font.montserrat_medium,
        weight = FontWeight.Medium,
        style = FontStyle.Normal,
    ),
    Font(
        resId = R.font.montserrat_semibold,
        weight = FontWeight.SemiBold,
        style = FontStyle.Normal,
    ),
    Font(
        resId = R.font.montserrat_bold,
        weight = FontWeight.Bold,
        style = FontStyle.Normal,
    ),
)

@Immutable
data class MorningFlowTypography(
    val brandLabel: TextStyle,
    val moduleTitle: TextStyle,
    val primaryCta: TextStyle,
    val bottomNavigationLabel: TextStyle,
    val sessionGuidance: TextStyle,
    val timeDisplay: TextStyle,
    val dialogTitle: TextStyle,
    val buttonLabel: TextStyle,
    val pageTitle: TextStyle,
    val actionLabel: TextStyle,
    val body: TextStyle,
    val sectionHeading: TextStyle,
    val timeFieldLabel: TextStyle,
    val dayChipLabel: TextStyle,
    val smallStrong: TextStyle,
    val smallRegular: TextStyle,
    val fieldLabel: TextStyle,
    val fieldValue: TextStyle,
    val stageTitle: TextStyle,
    val stageDuration: TextStyle,
    val statusBadge: TextStyle,
    val heroTitle: TextStyle,
    val countdownDisplay: TextStyle,
    val briefingLabel: TextStyle,
    val briefingValue: TextStyle,
    val supportingText: TextStyle,
    val permissionStatus: TextStyle,
    val fieldError: TextStyle,
    val reminderIntro: TextStyle,
    val reminderLocation: TextStyle,
    val reminderDescription: TextStyle,
    val completionTitle: TextStyle,
    val completionBody: TextStyle,
)

internal val MorningFlowTypographyTokens = MorningFlowTypography(
    brandLabel = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 21.sp,
    ),
    moduleTitle = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 31.5.sp,
    ),
    primaryCta = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.85.sp,
    ),
    bottomNavigationLabel = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 10.sp,
    ),
    sessionGuidance = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    timeDisplay = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 48.sp,
        lineHeight = 48.sp,
        letterSpacing = (-2.1).sp,
    ),
    dialogTitle = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.4).sp,
    ),
    buttonLabel = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
    ),
    pageTitle = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 52.5.sp,
        letterSpacing = (-1.05).sp,
    ),
    actionLabel = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 12.sp,
    ),
    body = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    sectionHeading = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    timeFieldLabel = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 14.25.sp,
    ),
    dayChipLabel = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
    ),
    smallStrong = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 19.5.sp,
    ),
    smallRegular = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 22.5.sp,
    ),
    fieldLabel = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    fieldValue = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 33.sp,
    ),
    stageTitle = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 28.5.sp,
    ),
    stageDuration = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 25.5.sp,
    ),
    statusBadge = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 20.sp,
    ),
    heroTitle = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 48.sp,
        lineHeight = 52.5.sp,
        letterSpacing = (-1.05).sp,
    ),
    countdownDisplay = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 96.sp,
        lineHeight = 132.84.sp,
        letterSpacing = 4.sp,
    ),
    briefingLabel = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 23.46.sp,
    ),
    briefingValue = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 26.22.sp,
    ),
    supportingText = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    permissionStatus = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    fieldError = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 14.25.sp,
    ),
    reminderIntro = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    reminderLocation = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
    ),
    reminderDescription = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.8.sp,
    ),
    completionTitle = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 52.5.sp,
        letterSpacing = (-1.05).sp,
    ),
    completionBody = TextStyle(
        fontFamily = MorningFlowFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 52.5.sp,
        letterSpacing = (-1.05).sp,
    ),
)
package com.morningflow.app.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.common.PrimaryButton
import com.morningflow.app.core.designsystem.component.navigation.EditorialArrowButton
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.designsystem.theme.illustrationFrame

@Composable
fun ColdStartScreen(
    data: ColdStartDisplayData,
    onBegin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(start = MorningFlowSpacing.Space32, top = MorningFlowSpacing.Space32),
            color = colors.textPrimary,
            style = typography.brandLabel,
        )
        Spacer(Modifier.height(128.dp))
        Column(modifier = Modifier.padding(horizontal = 40.dp)) {
            Text(
                text = stringResource(R.string.onboarding_cold_start_title),
                color = colors.textPrimary,
                style = typography.heroTitle,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space48))
            Text(
                text = stringResource(R.string.onboarding_cold_start_description),
                color = colors.textSecondary,
                style = typography.sessionGuidance,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            PrimaryButton(
                label = stringResource(R.string.onboarding_begin),
                onClick = onBegin,
            )
        }
        Spacer(Modifier.height(MorningFlowSpacing.Space48))
        Image(
            painter = painterResource(data.illustration),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).illustrationFrame(),
            contentScale = ContentScale.Fit,
        )
    }
}

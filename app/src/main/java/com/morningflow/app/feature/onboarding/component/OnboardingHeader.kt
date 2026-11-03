package com.morningflow.app.feature.onboarding.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

/** Brand, the page title and, where it is needed, one line on what the page is for. */
@Composable
fun OnboardingHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.app_name), color = colors.textPrimary, style = typography.brandLabel)
        Spacer(Modifier.height(MorningFlowSpacing.Space16))
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            color = colors.brandPrimary,
            maxLines = 1,
            softWrap = false,
            style = typography.pageTitle,
        )
        description?.let { Text(text = it, color = colors.textSecondary, style = typography.supportingText) }
    }
}

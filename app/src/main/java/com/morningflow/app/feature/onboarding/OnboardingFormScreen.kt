package com.morningflow.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.common.PrimaryButton
import com.morningflow.app.core.designsystem.component.navigation.EditorialArrowButton
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.feature.onboarding.component.OnboardingActiveDays
import com.morningflow.app.feature.onboarding.component.OnboardingHeader
import com.morningflow.app.feature.onboarding.component.OnboardingTimePickerField
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun OnboardingFormScreen(
    data: OnboardingFormDisplayData,
    visualState: OnboardingFormVisualState,
    onNext: () -> Unit,
    onTimeSelected: (OnboardingField, LocalTime) -> Unit = { _, _ -> },
    onDayToggle: (DayOfWeek) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val errors = data.errors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(MorningFlowSpacing.Space32),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            OnboardingHeader(title = stringResource(R.string.onboarding_setup_title))
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            OnboardingTimePickerField(
                label = stringResource(R.string.onboarding_earliest_wake_up),
                value = data.earliestWakeUp,
                onValueChange = { onTimeSelected(OnboardingField.EARLIEST_WAKE_UP, it) },
                modifier = Modifier.fillMaxWidth(),
                errorMessage = if (OnboardingField.EARLIEST_WAKE_UP in errors) {
                    stringResource(R.string.onboarding_time_required)
                } else null,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            OnboardingTimePickerField(
                label = stringResource(R.string.onboarding_latest_wake_up),
                value = data.latestWakeUp,
                onValueChange = { onTimeSelected(OnboardingField.LATEST_WAKE_UP, it) },
                modifier = Modifier.fillMaxWidth(),
                errorMessage = if (data.latestWakeUp == null && OnboardingField.LATEST_WAKE_UP in errors) {
                    stringResource(R.string.onboarding_time_required)
                } else if (OnboardingField.LATEST_WAKE_UP in errors) {
                    stringResource(R.string.onboarding_latest_time_error)
                } else null,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            OnboardingTimePickerField(
                label = stringResource(R.string.onboarding_ready_by),
                value = data.readyBy,
                onValueChange = { onTimeSelected(OnboardingField.READY_BY, it) },
                modifier = Modifier.fillMaxWidth(),
                errorMessage = if (data.readyBy == null && OnboardingField.READY_BY in errors) {
                    stringResource(R.string.onboarding_time_required)
                } else if (OnboardingField.READY_BY in errors) {
                    stringResource(R.string.onboarding_ready_time_error)
                } else null,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            OnboardingActiveDays(
                activeDays = data.activeDays,
                showError = OnboardingField.ACTIVE_DAYS in errors,
                onDayToggle = onDayToggle,
            )
        }
        PrimaryButton(
            label = stringResource(R.string.action_next),
            onClick = onNext,
        )
        Spacer(Modifier.height(MorningFlowSpacing.Space8))
    }
}

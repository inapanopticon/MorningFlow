package com.morningflow.app.core.designsystem.time

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.common.PrimaryButton
import com.morningflow.app.core.designsystem.component.common.SecondaryButton
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import java.time.LocalTime

/** A clock-face time picker in the app's own colours, using the phone's 12/24-hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningFlowTimePickerDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
) {
    val colors = MorningFlowDesignSystem.colors
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = ClockFormat.is24Hour,
    )
    val accentContainer = colors.brandPrimary.copy(alpha = 0.12f)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(MorningFlowShapes.Radius20)
                .background(colors.surfaceElevated)
                .padding(MorningFlowSpacing.Space24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (title != null) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.brandPrimary,
                    style = MorningFlowDesignSystem.typography.timeFieldLabel,
                )
                Spacer(Modifier.height(MorningFlowSpacing.Space16))
            }
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    containerColor = colors.surfaceElevated,
                    clockDialColor = colors.background,
                    clockDialSelectedContentColor = colors.surface,
                    clockDialUnselectedContentColor = colors.textPrimary,
                    selectorColor = colors.brandPrimary,
                    periodSelectorBorderColor = colors.borderInput,
                    periodSelectorSelectedContainerColor = accentContainer,
                    periodSelectorSelectedContentColor = colors.brandPrimary,
                    periodSelectorUnselectedContentColor = colors.textSecondary,
                    timeSelectorSelectedContainerColor = accentContainer,
                    timeSelectorSelectedContentColor = colors.brandPrimary,
                    timeSelectorUnselectedContainerColor = colors.background,
                    timeSelectorUnselectedContentColor = colors.textPrimary,
                ),
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
            ) {
                SecondaryButton(
                    label = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    label = stringResource(R.string.action_ok),
                    onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

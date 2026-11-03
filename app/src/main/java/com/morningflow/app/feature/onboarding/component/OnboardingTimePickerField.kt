package com.morningflow.app.feature.onboarding.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.designsystem.time.ClockFormat
import com.morningflow.app.core.designsystem.time.MorningFlowTimePickerDialog
import java.time.LocalTime

@Composable
fun OnboardingTimePickerField(
    label: String,
    value: LocalTime?,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    val colors = MorningFlowDesignSystem.colors
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        MorningFlowTimePickerDialog(
            initial = value ?: LocalTime.of(7, 0),
            title = label,
            onConfirm = {
                showPicker = false
                onValueChange(it)
            },
            onDismiss = { showPicker = false },
        )
    }
    Column(
        modifier = modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.a11y_change_time)) {
            showPicker = true
        },
    ) {
        Row {
            Text(
                text = label,
                color = colors.brandPrimary,
                style = MorningFlowDesignSystem.typography.timeFieldLabel,
            )
            if (errorMessage != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(MorningFlowSpacing.Space8))
                Text(
                    text = errorMessage,
                    color = colors.warning,
                    style = MorningFlowDesignSystem.typography.fieldError,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = value?.let(ClockFormat::format) ?: "--:--",
                color = if (value == null) colors.borderInput else colors.textPrimary,
                style = MorningFlowDesignSystem.typography.timeDisplay,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .drawBehind {
                    drawLine(
                        color = colors.borderInput,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        )
    }
}

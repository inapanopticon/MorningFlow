package com.morningflow.app.feature.onboarding.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import java.time.DayOfWeek

@Composable
fun OnboardingActiveDays(
    activeDays: Set<DayOfWeek>,
    showError: Boolean,
    onDayToggle: (DayOfWeek) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography

    Column(modifier = modifier.fillMaxWidth()) {
        Row {
            Text(
                text = stringResource(R.string.onboarding_active_days),
                color = colors.brandPrimary,
                style = typography.timeFieldLabel,
            )
            if (showError) {
                Spacer(Modifier.width(MorningFlowSpacing.Space8))
                Text(
                    text = stringResource(R.string.onboarding_active_days_error),
                    color = colors.warning,
                    style = typography.fieldError,
                )
            }
        }
        Spacer(Modifier.height(MorningFlowSpacing.Space16))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DayOfWeek.entries.forEach { day ->
                val selected = day in activeDays
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.brandPrimary else Color.Transparent)
                        .border(1.dp, if (selected) colors.brandPrimary else colors.borderInput, CircleShape)
                        .toggleable(
                            value = selected,
                            role = Role.Checkbox,
                            onValueChange = { onDayToggle(day) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.name.take(3),
                        color = if (selected) colors.surface else colors.textSecondary,
                        style = typography.dayChipLabel,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = colors.borderInput)
    }
}

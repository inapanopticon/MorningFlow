package com.morningflow.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.common.PrimaryButton
import com.morningflow.app.core.designsystem.component.navigation.EditorialArrowButton
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.feature.onboarding.component.OnboardingHeader

@Composable
private fun PermissionRow(
    label: String,
    description: String,
    access: PermissionAccess,
    onClick: (() -> Unit)? = null,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MorningFlowSpacing.Space12)) {
            Text(text = label, color = colors.textPrimary, style = typography.moduleTitle)
            Text(text = description, color = colors.textSecondary, style = typography.body)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    when (access) {
                        PermissionAccess.Granted -> R.string.permission_granted
                        PermissionAccess.Denied -> R.string.permission_denied
                        PermissionAccess.NotAvailable -> R.string.permission_not_available
                    },
                ),
                color = when (access) {
                    PermissionAccess.Granted -> colors.textPrimary
                    // Not granted yet reads as an action, since tapping the row asks for it.
                    PermissionAccess.Denied -> colors.brandPrimary
                    PermissionAccess.NotAvailable -> colors.textSecondary
                },
                style = typography.permissionStatus,
            )
        }
    }
    HorizontalDivider(color = colors.borderInput)
}
@Composable
fun PermissionScreen(
    data: PermissionDisplayData,
    onNext: () -> Unit,
    onLocationRequest: (() -> Unit)? = null,
    onCalendarRequest: (() -> Unit)? = null,
    onSensorsRequest: (() -> Unit)? = null,
    onNotificationsRequest: (() -> Unit)? = null,
    onExactAlarmRequest: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(MorningFlowSpacing.Space32),
    ) {
        OnboardingHeader(
            title = stringResource(R.string.permission_title),
            description = stringResource(R.string.permission_description),
        )
        Spacer(Modifier.height(18.dp))
        PermissionRow(
            label = stringResource(R.string.permission_location),
            description = stringResource(R.string.permission_location_description),
            access = data.location,
            onClick = onLocationRequest,
        )
        PermissionRow(
            label = stringResource(R.string.permission_calendar),
            description = stringResource(R.string.permission_calendar_description),
            access = data.calendar,
            onClick = onCalendarRequest,
        )
        PermissionRow(
            label = stringResource(R.string.permission_sensors),
            description = stringResource(R.string.permission_sensors_description),
            access = data.sensors,
            onClick = onSensorsRequest,
        )
        PermissionRow(
            label = stringResource(R.string.permission_notifications),
            description = stringResource(R.string.permission_notifications_description),
            access = data.notifications,
            onClick = onNotificationsRequest,
        )
        PermissionRow(
            label = stringResource(R.string.permission_alarms_and_reminders),
            description = stringResource(R.string.permission_alarms_description),
            access = data.exactAlarm,
            onClick = exactAlarmRequestAction(data.exactAlarm, onExactAlarmRequest),
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            label = stringResource(R.string.action_next),
            onClick = { dispatchPermissionsNext(onNext) },
        )
        Spacer(Modifier.height(MorningFlowSpacing.Space8))
    }
}

/** Keeps the permission page's NEXT action explicit and directly testable. */
internal fun dispatchPermissionsNext(onNext: () -> Unit) {
    onNext()
}

internal fun exactAlarmRequestAction(
    access: PermissionAccess,
    onRequest: (() -> Unit)?,
): (() -> Unit)? = if (access == PermissionAccess.Denied) onRequest else null

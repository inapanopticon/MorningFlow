package com.morningflow.app.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.feature.home.HomeCalendarState
import com.morningflow.app.feature.home.HomeLocationState
import com.morningflow.app.feature.home.HomeUiState
import com.morningflow.app.feature.home.HomeWeatherState
import com.morningflow.app.feature.home.toHomeNextEventLabel

@Composable
private fun BriefingRow(
    label: String? = null,
    value: String? = null,
    suffix: String? = null,
    trailing: String? = null,
    onTrailingClick: () -> Unit = {},
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (trailing == null) {
            Arrangement.spacedBy(MorningFlowSpacing.Space6)
        } else {
            Arrangement.SpaceBetween
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label != null) {
                Text(text = label, color = colors.textSecondary, style = typography.briefingLabel)
            }
            if (value != null) {
                Text(text = value, color = colors.textPrimary, style = typography.briefingValue)
            }
            if (suffix != null) {
                Text(text = suffix, color = colors.textSecondary, style = typography.briefingLabel)
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                color = colors.textSecondary,
                style = typography.briefingLabel,
                modifier = Modifier.clickable(onClick = onTrailingClick),
            )
        }
    }
}

@Composable
fun HomeBriefing(
    state: HomeUiState,
    onOpenSettingsPermissions: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val openSettings = stringResource(R.string.home_open_settings)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space6),
    ) {
        when (val location = state.location) {
            is HomeLocationState.Available -> {
                when (state.weather) {
                    is HomeWeatherState.Available -> BriefingRow(
                        value = "${state.weather.temperatureCelsius}°",
                        suffix = location.label,
                    )
                    else -> BriefingRow(value = location.label)
                }
            }
            HomeLocationState.PermissionDenied -> BriefingRow(
                label = stringResource(R.string.home_location_unavailable),
                trailing = openSettings,
                onTrailingClick = onOpenSettingsPermissions,
            )
            is HomeLocationState.Unavailable,
            HomeLocationState.Loading,
            -> BriefingRow(
                label = stringResource(R.string.home_location_unavailable),
                trailing = null,
                onTrailingClick = onOpenSettingsPermissions,
            )
        }

        when (val weather = state.weather) {
            is HomeWeatherState.Available -> BriefingRow(
                stringResource(R.string.home_weather_prefix),
                value = weather.condition,
                suffix = stringResource(R.string.home_weather_suffix),
            )
            is HomeWeatherState.Unavailable,
            HomeWeatherState.Loading,
            -> BriefingRow(
                label = stringResource(R.string.home_weather_unavailable),
                trailing = if (state.location is HomeLocationState.PermissionDenied) {
                    openSettings
                } else {
                    null
                },
                onTrailingClick = onOpenSettingsPermissions,
            )
        }

        when (val calendar = state.calendar) {
            is HomeCalendarState.Available -> BriefingRow(
                stringResource(R.string.home_next_todo),
                value = calendar.nextEvent.toHomeNextEventLabel(),
            )
            HomeCalendarState.Empty -> BriefingRow(
                stringResource(R.string.home_next_todo),
                value = "Nothing scheduled next.",
            )
            is HomeCalendarState.Unavailable -> BriefingRow(
                label = stringResource(R.string.home_todo_unavailable),
                trailing = if (
                    calendar.reason == "PERMISSION_DENIED"
                ) {
                    openSettings
                } else {
                    null
                },
                onTrailingClick = onOpenSettingsPermissions,
            )
            HomeCalendarState.Loading -> BriefingRow(
                label = stringResource(R.string.home_calendar_checking),
            )
        }
    }
}

package com.morningflow.app.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.domain.weatherlocation.WeatherLocationCoordinator
import com.morningflow.app.core.domain.weatherlocation.WeatherLocationUiState
import com.morningflow.app.core.model.location.LocationSnapshot
import com.morningflow.app.core.model.weather.WeatherSnapshot
import kotlinx.coroutines.launch

@Composable
fun WeatherLocationDebugScreen(
    coordinator: WeatherLocationCoordinator,
    modifier: Modifier = Modifier,
) {
    val state by coordinator.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Weather debug",
            color = colors.textPrimary,
            style = typography.pageTitle,
        )
        Text(
            text = "Debug-only view of the domain Coordinator and raw Snapshots.",
            color = colors.textSecondary,
            style = typography.body,
        )

        DebugSection(title = "State") {
            Text(
                text = state.label(),
                color = colors.brandPrimary,
                style = typography.sectionHeading,
            )
            Text(
                text = "Refresh interval: ${coordinator.refreshIntervalMinutes} minutes",
                color = colors.textSecondary,
                style = typography.body,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { coordinator.refresh() } }) {
                Text("Refresh")
            }
            OutlinedButton(onClick = { coordinator.setRefreshIntervalMinutes(5) }) {
                Text("5 min")
            }
            OutlinedButton(onClick = { coordinator.setRefreshIntervalMinutes(15) }) {
                Text("15 min")
            }
            OutlinedButton(onClick = { coordinator.setRefreshIntervalMinutes(60) }) {
                Text("60 min")
            }
        }

        when (state) {
            is WeatherLocationUiState.Ready -> {
                val ready = state as WeatherLocationUiState.Ready
                LocationSnapshotCard(ready.location)
                WeatherSnapshotCard(ready.weather, stale = false)
            }

            is WeatherLocationUiState.ReadyWithStaleWeather -> {
                val stale = state as WeatherLocationUiState.ReadyWithStaleWeather
                LocationSnapshotCard(stale.location)
                WeatherSnapshotCard(stale.weather, stale = true)
            }

            is WeatherLocationUiState.WeatherUnavailable -> {
                val unavailable = state as WeatherLocationUiState.WeatherUnavailable
                LocationSnapshotCard(unavailable.location)
                DebugSection(title = "Weather") {
                    Text(
                        text = "Unavailable: ${unavailable.reason}",
                        color = colors.warning,
                        style = typography.body,
                    )
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun LocationSnapshotCard(location: LocationSnapshot) {
    DebugSection(title = "LocationSnapshot") {
        DebugValue("displayLabel", location.displayLabel)
        DebugValue("latitude", location.latitude.toString())
        DebugValue("longitude", location.longitude.toString())
        DebugValue("accuracyMeters", location.accuracyMeters?.toString() ?: "null")
        DebugValue("capturedAtEpochMs", location.capturedAtEpochMs.toString())
        DebugValue("district", location.district ?: "null")
        DebugValue("city", location.city ?: "null")
        DebugValue("adminArea", location.adminArea ?: "null")
        DebugValue("countryCode", location.countryCode ?: "null")
        DebugValue("source", location.source.name)
    }
}

@Composable
private fun WeatherSnapshotCard(weather: WeatherSnapshot, stale: Boolean) {
    DebugSection(title = if (stale) "WeatherSnapshot (stale)" else "WeatherSnapshot") {
        DebugValue("locationKey", weather.locationKey)
        DebugValue("locationLabel", weather.locationLabel)
        DebugValue("temperatureCelsius", weather.temperatureCelsius.toString())
        DebugValue(
            "apparentTemperatureCelsius",
            weather.apparentTemperatureCelsius?.toString() ?: "null",
        )
        DebugValue(
            "dailyMaxTemperatureCelsius",
            weather.dailyMaxTemperatureCelsius?.toString() ?: "null",
        )
        DebugValue(
            "dailyMinTemperatureCelsius",
            weather.dailyMinTemperatureCelsius?.toString() ?: "null",
        )
        DebugValue("condition", weather.condition.name)
        DebugValue("rainProbability", weather.rainProbability?.toString() ?: "null")
        DebugValue("uvIndex", weather.uvIndex?.toString() ?: "null")
        DebugValue("fetchedAtEpochMs", weather.fetchedAtEpochMs.toString())
    }
}

@Composable
private fun DebugSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = MorningFlowDesignSystem.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                style = MorningFlowDesignSystem.typography.moduleTitle,
            )
            content()
        }
    }
}

@Composable
private fun DebugValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MorningFlowDesignSystem.colors.textSecondary,
            style = MorningFlowDesignSystem.typography.actionLabel,
        )
        Text(
            text = value,
            color = MorningFlowDesignSystem.colors.textPrimary,
            style = MorningFlowDesignSystem.typography.body,
        )
    }
}

private fun WeatherLocationUiState.label(): String = when (this) {
    WeatherLocationUiState.Idle -> "Idle"
    WeatherLocationUiState.Loading -> "Loading"
    WeatherLocationUiState.PermissionRequired -> "PermissionRequired"
    is WeatherLocationUiState.LocationUnavailable -> "LocationUnavailable: $reason"
    is WeatherLocationUiState.WeatherUnavailable -> "WeatherUnavailable: $reason"
    is WeatherLocationUiState.Ready -> "Ready"
    is WeatherLocationUiState.ReadyWithStaleWeather -> "ReadyWithStaleWeather: $reason"
}

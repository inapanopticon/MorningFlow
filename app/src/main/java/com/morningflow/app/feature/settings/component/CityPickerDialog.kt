package com.morningflow.app.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.morningflow.app.R
import com.morningflow.app.core.data.location.impl.CitySuggestion
import com.morningflow.app.core.designsystem.component.common.SecondaryButton
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.model.DataResult
import kotlinx.coroutines.delay

/**
 * Search for a city by name, or go back to the phone's own location. Searching waits for
 * a short pause in typing so every keystroke does not hit the network.
 */
@Composable
fun CityPickerDialog(
    hasManualCity: Boolean,
    search: suspend (String) -> DataResult<List<CitySuggestion>>,
    onChoose: (CitySuggestion) -> Unit,
    onUseDeviceLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CitySuggestion>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(350)
        when (val result = search(query)) {
            is DataResult.Success -> { results = result.value; failed = false }
            is DataResult.Unavailable -> { results = emptyList(); failed = true }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(MorningFlowShapes.Radius20)
                .background(colors.surfaceElevated)
                .padding(MorningFlowSpacing.Space24),
            verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
        ) {
            Text(
                text = stringResource(R.string.settings_city_title),
                color = colors.foregroundEmphasis,
                style = typography.dialogTitle,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.settings_city_hint)) },
                singleLine = true,
                shape = MorningFlowShapes.Radius12,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.brandPrimary,
                    unfocusedBorderColor = colors.borderInput,
                    cursorColor = colors.brandPrimary,
                ),
            )
            Column(
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
            ) {
                if (failed) {
                    Text(
                        text = stringResource(R.string.settings_city_search_failed),
                        color = colors.warning,
                        style = typography.body,
                    )
                } else if (query.trim().length >= 2 && results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_city_no_results),
                        color = colors.textSecondary,
                        style = typography.body,
                    )
                }
                results.forEach { city ->
                    Text(
                        text = city.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MorningFlowShapes.Radius8)
                            .clickable { onChoose(city) }
                            .padding(vertical = MorningFlowSpacing.Space12, horizontal = MorningFlowSpacing.Space4),
                        color = colors.textPrimary,
                        style = typography.body,
                    )
                    HorizontalDivider(color = colors.divider)
                }
            }
            if (hasManualCity) {
                Text(
                    text = stringResource(R.string.settings_city_use_device),
                    modifier = Modifier
                        .clip(MorningFlowShapes.Radius8)
                        .clickable(onClick = onUseDeviceLocation)
                        .padding(vertical = MorningFlowSpacing.Space8),
                    color = colors.brandPrimary,
                    style = typography.buttonLabel,
                )
            }
            Spacer(Modifier.height(MorningFlowSpacing.Space4))
            SecondaryButton(label = stringResource(R.string.action_cancel), onClick = onDismiss)
        }
    }
}

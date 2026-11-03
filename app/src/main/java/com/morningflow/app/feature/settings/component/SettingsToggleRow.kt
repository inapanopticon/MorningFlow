package com.morningflow.app.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.form.ModuleSwitch
import com.morningflow.app.core.designsystem.component.form.ModuleSwitchSize
import com.morningflow.app.core.designsystem.icon.MorningFlowIcons
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

@Composable
fun SettingsToggleRow(
    label: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    description: String? = null,
    volume: Float? = null,
    onVolumeChanged: ((Float) -> Unit)? = null,
) {
    val colors = MorningFlowDesignSystem.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(33.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, color = colors.textPrimary, style = MorningFlowDesignSystem.typography.fieldValue)
            SettingsOnOffValue(enabled = enabled, onClick = onClick, label = label)
        }
        description?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth(),
                color = colors.textSecondary,
                style = MorningFlowDesignSystem.typography.body,
            )
        }
        volume?.let { value ->
            SettingsVolume(value = value, onValueChanged = onVolumeChanged, label = label)
        }
    }
}

@Composable
private fun SettingsOnOffValue(
    enabled: Boolean,
    onClick: (() -> Unit)?,
    label: String,
) {
    ModuleSwitch(ModuleSwitchSize.Standard, checked = enabled, onClick = onClick, label = label)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsVolume(
    value: Float,
    onValueChanged: ((Float) -> Unit)?,
    label: String,
) {
    val volumeLabel = stringResource(R.string.a11y_volume_format, label)
    val colors = MorningFlowDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(MorningFlowIcons.Speaker),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colors.textSecondary,
        )
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { onValueChanged?.invoke(it) },
            enabled = onValueChanged != null,
            modifier = Modifier.weight(1f).height(32.dp).semantics { contentDescription = volumeLabel },
            colors = SliderDefaults.colors(
                thumbColor = colors.brandPrimary,
                activeTrackColor = colors.brandPrimary,
                inactiveTrackColor = colors.borderSubtle,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(colors.brandPrimary),
                )
            },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    modifier = Modifier.height(4.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = colors.brandPrimary,
                        inactiveTrackColor = colors.borderSubtle,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                )
            },
        )
    }
}

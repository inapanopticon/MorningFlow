package com.morningflow.app.core.designsystem.icon

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.designsystem.theme.MorningFlowTheme

@Composable
private fun IconSample(@DrawableRes icon: Int, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = Color.Unspecified,
        )
        Text(
            text = label,
            color = MorningFlowDesignSystem.colors.textPrimary,
            style = MorningFlowDesignSystem.typography.body,
        )
    }
}

@Preview(name = "MorningFlow Icons", showBackground = true)
@Composable
private fun MorningFlowIconsPreview() {
    MorningFlowTheme {
        Surface(color = MorningFlowDesignSystem.colors.background) {
            Column(
                modifier = Modifier.padding(MorningFlowSpacing.Space24),
                verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
            ) {
                IconSample(MorningFlowIcons.EditorialArrow, "ic_editorial_arrow")
                IconSample(MorningFlowIcons.ThermometerSnowflake, "ic_thermometer_snowflake")
                IconSample(MorningFlowIcons.Umbrella, "ic_umbrella")
                IconSample(MorningFlowIcons.Sun, "ic_sun")
                IconSample(MorningFlowIcons.Close, "ic_close")
                IconSample(MorningFlowIcons.Pause, "ic_pause")
                IconSample(MorningFlowIcons.Play, "ic_play")
                IconSample(MorningFlowIcons.Check, "ic_check")
                IconSample(MorningFlowIcons.NavHome, "ic_nav_home")
                IconSample(MorningFlowIcons.NavTemplates, "ic_nav_templates")
                IconSample(MorningFlowIcons.NavSettings, "ic_nav_settings")
                IconSample(MorningFlowIcons.ChevronLeft, "ic_chevron_left")
                IconSample(MorningFlowIcons.ArrowUp, "ic_arrow_up")
                IconSample(MorningFlowIcons.ArrowDown, "ic_arrow_down")
            }
        }
    }
}

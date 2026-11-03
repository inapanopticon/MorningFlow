package com.morningflow.app.core.designsystem.component.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.icon.MorningFlowIcons
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

enum class MorningFlowNavigationDestination(
    val label: String,
    @param:DrawableRes val icon: Int,
) {
    Home("Home", MorningFlowIcons.NavHome),
    Templates("Templates", MorningFlowIcons.NavTemplates),
    History("History", MorningFlowIcons.NavHistory),
    Settings("Settings", MorningFlowIcons.NavSettings),
}

@Composable
fun MorningFlowBottomNavigation(
    selected: MorningFlowNavigationDestination,
    onSelected: (MorningFlowNavigationDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    Row(
        modifier = modifier
            .widthIn(min = 310.dp)
            .heightIn(min = 48.dp)
            .clip(MorningFlowShapes.Radius24)
            .background(colors.navSurface)
            .padding(horizontal = MorningFlowSpacing.Space4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MorningFlowNavigationDestination.entries.forEach { destination ->
            val isSelected = selected == destination
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(if (isSelected) MorningFlowShapes.Radius24 else MorningFlowShapes.Radius20)
                    .background(if (isSelected) colors.navItemActive else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelected(destination) })
                    .padding(horizontal = MorningFlowSpacing.Space12),
                horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(destination.icon),
                    // The label next to it already names the tab.
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isSelected) colors.background else colors.navItemInactive,
                )
                Text(
                    text = destination.label.uppercase(),
                    color = if (isSelected) colors.background else colors.navItemInactive,
                    style = MorningFlowDesignSystem.typography.bottomNavigationLabel,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Preview(name = "Bottom Navigation Variants", showBackground = true)
@Composable
private fun MorningFlowBottomNavigationPreview() {
    MorningFlowComponentPreview {
        MorningFlowNavigationDestination.entries.forEach { destination ->
            MorningFlowBottomNavigation(selected = destination, onSelected = {})
        }
    }
}

package com.morningflow.app.core.designsystem.component.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

enum class ModuleSwitchSize(val width: Dp, val height: Dp, val knob: Dp) {
    Standard(46.dp, 26.dp, 22.dp),
    Compact(42.dp, 24.dp, 20.dp),
}

@Composable
fun ModuleSwitch(
    size: ModuleSwitchSize,
    modifier: Modifier = Modifier,
    checked: Boolean = true,
    onClick: (() -> Unit)? = null,
    /** What the switch controls, read by screen readers along with its on/off state. */
    label: String? = null,
) {
    val colors = MorningFlowDesignSystem.colors
    val background = if (checked) colors.brandPrimary else colors.surface
    Box(
        modifier = modifier
            .size(width = size.width, height = size.height)
            .clip(MorningFlowShapes.Full)
            .background(background)
            .border(1.dp, if (checked) background else colors.borderDefault, MorningFlowShapes.Full)
            // The name goes on before toggleable, so both land on the one node a screen
            // reader announces: "Background music, on, switch".
            .then(if (label == null) Modifier else Modifier.semantics { contentDescription = label })
            .then(
                if (onClick == null) Modifier
                else Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { onClick() }),
            )
            .padding(MorningFlowSpacing.Space2),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(size.knob)
                .clip(MorningFlowShapes.Full)
                .background(colors.surface),
        )
    }
}

@Preview(name = "Module Switch Variants", showBackground = true)
@Composable
private fun ModuleSwitchPreview() {
    MorningFlowComponentPreview {
        ModuleSwitch(ModuleSwitchSize.Standard)
        ModuleSwitch(ModuleSwitchSize.Compact)
    }
}

package com.morningflow.app.core.designsystem.component.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.component.ComponentPreviewData
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

/** The one filled call to action on a screen: full width, brand green, pill shaped. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MorningFlowDesignSystem.colors
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        enabled = enabled,
        shape = MorningFlowShapes.Full,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.brandPrimary,
            contentColor = colors.surface,
            disabledContainerColor = colors.borderInput,
            disabledContentColor = colors.surface,
        ),
        contentPadding = PaddingValues(horizontal = MorningFlowSpacing.Space24),
    ) {
        Text(
            text = label,
            style = MorningFlowDesignSystem.typography.buttonLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A secondary action beside or below a [PrimaryButton]. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MorningFlowDesignSystem.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        enabled = enabled,
        shape = MorningFlowShapes.Full,
        border = BorderStroke(1.dp, colors.borderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surfaceElevated,
            contentColor = colors.foregroundEmphasis,
        ),
        contentPadding = PaddingValues(horizontal = MorningFlowSpacing.Space24),
    ) {
        Text(
            text = label,
            style = MorningFlowDesignSystem.typography.buttonLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(name = "Buttons", showBackground = true)
@Composable
private fun MorningFlowButtonsPreview() {
    MorningFlowComponentPreview {
        PrimaryButton(label = ComponentPreviewData.PrimaryAction, onClick = {})
        SecondaryButton(label = ComponentPreviewData.Cancel, onClick = {})
    }
}

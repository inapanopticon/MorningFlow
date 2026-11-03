package com.morningflow.app.core.designsystem.component.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.morningflow.app.core.designsystem.component.ComponentPreviewData
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowElevation
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

/**
 * Asks before a destructive action. Shown in its own window so the screen behind is dimmed
 * and blocked, and Back or a tap outside counts as [onCancel]. The safe choice is the filled
 * button; the destructive one is a quieter text button in the warning colour.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ConfirmationDialogContent(
            title = title,
            confirmLabel = confirmLabel,
            cancelLabel = cancelLabel,
            onConfirm = onConfirm,
            onCancel = onCancel,
            modifier = modifier.padding(horizontal = MorningFlowSpacing.Space24),
            description = description,
        )
    }
}

@Composable
private fun ConfirmationDialogContent(
    title: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val colors = MorningFlowDesignSystem.colors
    val shadow = MorningFlowElevation.Dialog
    Column(
        modifier = modifier
            .widthIn(max = 342.dp)
            .fillMaxWidth()
            .shadow(
                elevation = shadow.yOffset,
                shape = MorningFlowShapes.Radius20,
                ambientColor = shadow.color,
                spotColor = shadow.color,
            )
            .clip(MorningFlowShapes.Radius20)
            .background(colors.surfaceElevated)
            .padding(
                start = MorningFlowSpacing.Space24,
                top = MorningFlowSpacing.Space24,
                end = MorningFlowSpacing.Space24,
                bottom = MorningFlowSpacing.Space12,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            color = colors.foregroundEmphasis,
            style = MorningFlowDesignSystem.typography.dialogTitle,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(MorningFlowSpacing.Space12))
            Text(
                text = description,
                modifier = Modifier.fillMaxWidth(),
                color = colors.textSecondary,
                style = MorningFlowDesignSystem.typography.body,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(MorningFlowSpacing.Space24))
        PrimaryButton(label = cancelLabel, onClick = onCancel)
        Spacer(modifier = Modifier.height(MorningFlowSpacing.Space4))
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.warning),
        ) {
            Text(text = confirmLabel, style = MorningFlowDesignSystem.typography.buttonLabel)
        }
    }
}

@Preview(name = "Confirmation Dialog", showBackground = true, widthDp = 390, heightDp = 320)
@Composable
private fun ConfirmationDialogPreview() {
    MorningFlowComponentPreview {
        ConfirmationDialogContent(
            title = ComponentPreviewData.DialogTitle,
            description = ComponentPreviewData.Body,
            confirmLabel = ComponentPreviewData.Confirm,
            cancelLabel = ComponentPreviewData.Cancel,
            onConfirm = {},
            onCancel = {},
        )
    }
}

package com.morningflow.app.core.designsystem.component.form

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.component.ComponentPreviewData
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

enum class TemplateFormFieldState { Normal, Error }

@Composable
fun TemplateFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    state: TemplateFormFieldState,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    enabled: Boolean = true,
) {
    val colors = MorningFlowDesignSystem.colors
    Column(
        modifier = modifier.width(342.dp).height(78.dp),
        verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
    ) {
        Row(
            modifier = Modifier.height(18.dp),
            horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, color = colors.brandPrimary, style = MorningFlowDesignSystem.typography.fieldLabel)
            if (state == TemplateFormFieldState.Error && errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = colors.warning,
                    style = MorningFlowDesignSystem.typography.body,
                )
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = !enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(1.dp, colors.textPrimary, MorningFlowShapes.Radius8)
                .padding(horizontal = MorningFlowSpacing.Space16),
            singleLine = true,
            textStyle = MorningFlowDesignSystem.typography.fieldValue.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.brandPrimary),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    innerTextField()
                }
            },
        )
    }
}

@Preview(name = "Template Form Field Variants", showBackground = true)
@Composable
private fun TemplateFormFieldPreview() {
    MorningFlowComponentPreview {
        TemplateFormField(
            label = ComponentPreviewData.FieldLabel,
            value = ComponentPreviewData.FieldValue,
            onValueChange = {},
            state = TemplateFormFieldState.Normal,
        )
        TemplateFormField(
            label = ComponentPreviewData.FieldLabel,
            value = ComponentPreviewData.FieldValue,
            onValueChange = {},
            state = TemplateFormFieldState.Error,
            errorMessage = ComponentPreviewData.Error,
        )
    }
}

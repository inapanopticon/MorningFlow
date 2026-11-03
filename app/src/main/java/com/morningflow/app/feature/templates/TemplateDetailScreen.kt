package com.morningflow.app.feature.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.common.ConfirmationDialog
import com.morningflow.app.core.designsystem.component.common.PrimaryButton
import com.morningflow.app.core.designsystem.component.common.SecondaryButton
import com.morningflow.app.core.designsystem.component.navigation.EditorTopBar
import com.morningflow.app.core.designsystem.component.template.TemplateStageRow
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

@Composable
fun TemplateDetailScreen(
    template: TemplateSummaryDisplayData,
    variant: TemplateDetailVariant,
    dialog: TemplateDeleteDialogDisplayData,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(25.dp))
            EditorTopBar(
                title = stringResource(R.string.template_top_bar_title),
                action = stringResource(R.string.template_edit),
                onBack = onBack,
                onAction = onEdit,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            Column(modifier = Modifier.padding(horizontal = MorningFlowSpacing.Space32)) {
                Text(
                    text = template.title,
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.brandPrimary,
                    style = MorningFlowDesignSystem.typography.pageTitle,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.template_detail_summary_format,
                        template.moduleCount,
                        template.moduleCount,
                        template.totalDurationMinutes,
                        template.applicableDays.toTemplateDayRangeText(),
                    ),
                    color = colors.textSecondary,
                    style = MorningFlowDesignSystem.typography.stageDuration,
                )
                Spacer(Modifier.height(31.dp))
                Column(verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12)) {
                    template.modules.filter { it.enabled }.forEachIndexed { index, module ->
                        TemplateStageRow(
                            sequence = "%02d".format(index + 1),
                            title = module.title,
                            duration = module.durationMinutes.toStageDurationText(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 39.dp, vertical = 39.dp),
            verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
        ) {
            PrimaryButton(
                label = stringResource(R.string.action_start),
                onClick = onStart,
            )
            if (template.isDeletable) {
                SecondaryButton(
                    label = stringResource(R.string.template_delete),
                    onClick = onDelete,
                )
            }
        }

        if (variant == TemplateDetailVariant.DeleteConfirmation) {
            ConfirmationDialog(
                title = stringResource(dialog.title),
                confirmLabel = stringResource(dialog.confirmLabel),
                cancelLabel = stringResource(dialog.cancelLabel),
                onConfirm = onConfirmDelete,
                onCancel = onDismissDelete,
            )
        }
    }
}

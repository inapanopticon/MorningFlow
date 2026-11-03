package com.morningflow.app.feature.templates.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.template.TemplateCardSurface
import com.morningflow.app.core.designsystem.icon.MorningFlowIcons
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.feature.templates.TemplateSummaryDisplayData
import com.morningflow.app.feature.templates.toTemplateDaysText

@Composable
fun TemplateListCard(
    template: TemplateSummaryDisplayData,
    onStart: () -> Unit,
    onDetails: () -> Unit,
    onSetDefault: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    val detailsLabel = stringResource(R.string.template_details)

    // The whole card opens the template; Start is the one shortcut on top of it.
    TemplateCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MorningFlowShapes.Radius20)
            .clickable(onClickLabel = detailsLabel, onClick = onDetails),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space4),
            ) {
                Text(
                    text = template.title,
                    color = colors.textPrimary,
                    style = typography.moduleTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.template_summary_format,
                        template.moduleCount,
                        template.moduleCount,
                        template.totalDurationMinutes,
                    ),
                    color = colors.textSecondary,
                    style = typography.stageDuration,
                )
                Text(
                    text = template.applicableDays.toTemplateDaysText(),
                    color = colors.textSecondary,
                    style = typography.stageDuration,
                )
            }
            Spacer(Modifier.width(MorningFlowSpacing.Space12))
            Button(
                onClick = onStart,
                modifier = Modifier.heightIn(min = 40.dp),
                shape = MorningFlowShapes.Full,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandPrimary,
                    contentColor = colors.surface,
                ),
                contentPadding = PaddingValues(horizontal = MorningFlowSpacing.Space16),
            ) {
                Icon(
                    painter = painterResource(MorningFlowIcons.Play),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(MorningFlowSpacing.Space6))
                Text(text = stringResource(R.string.action_start), style = typography.buttonLabel)
            }
        }
        if (template.isDefault) {
            Text(
                text = stringResource(R.string.template_default_badge),
                modifier = Modifier
                    .clip(MorningFlowShapes.Full)
                    .background(colors.brandPrimary.copy(alpha = 0.1f))
                    .padding(horizontal = MorningFlowSpacing.Space8, vertical = 2.dp),
                color = colors.brandPrimary,
                style = typography.smallStrong,
            )
        } else if (onSetDefault != null) {
            Text(
                text = stringResource(R.string.template_set_default),
                modifier = Modifier
                    .clip(MorningFlowShapes.Full)
                    .clickable(onClick = onSetDefault)
                    .padding(vertical = MorningFlowSpacing.Space4),
                color = colors.brandPrimary,
                style = typography.smallStrong,
            )
        }
    }
}

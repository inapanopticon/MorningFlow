package com.morningflow.app.feature.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowBottomNavigation
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowNavigationDestination
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.feature.templates.component.TemplateListCard

@Composable
fun TemplatesListScreen(
    templates: List<TemplateSummaryDisplayData>,
    onCreateTemplate: () -> Unit,
    onStart: (TemplateSummaryDisplayData) -> Unit,
    onDetails: (TemplateSummaryDisplayData) -> Unit,
    onSetDefault: (TemplateSummaryDisplayData) -> Unit = {},
    onNavigationSelected: (MorningFlowNavigationDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MorningFlowSpacing.Space32),
        ) {
            Spacer(Modifier.height(MorningFlowSpacing.Space32))
            Row(
                modifier = Modifier.fillMaxWidth().height(21.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.templates_header_brand),
                    color = colors.textPrimary,
                    style = MorningFlowDesignSystem.typography.brandLabel,
                )
                Text(
                    text = stringResource(R.string.templates_header_section),
                    color = colors.textPrimary,
                    style = MorningFlowDesignSystem.typography.brandLabel,
                )
            }
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            Text(
                text = stringResource(R.string.templates_title),
                modifier = Modifier.fillMaxWidth().semantics { heading() },
                maxLines = 1,
                softWrap = false,
                color = colors.brandPrimary,
                style = MorningFlowDesignSystem.typography.pageTitle,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(MorningFlowShapes.Radius24)
                    .border(1.5.dp, colors.textPrimary, MorningFlowShapes.Radius24)
                    .clickable(onClick = onCreateTemplate),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.templates_create),
                    color = colors.textPrimary,
                    style = MorningFlowDesignSystem.typography.buttonLabel,
                )
                Spacer(Modifier.width(MorningFlowSpacing.Space8))
                Text(
                    text = stringResource(R.string.template_increment),
                    color = colors.textPrimary,
                    style = MorningFlowDesignSystem.typography.sectionHeading,
                )
            }
            Spacer(Modifier.height(MorningFlowSpacing.Space24))

            templates.forEach { template ->
                TemplateListCard(
                    template = template,
                    onStart = { onStart(template) },
                    onDetails = { onDetails(template) },
                    onSetDefault = { onSetDefault(template) },
                )
                Spacer(Modifier.height(MorningFlowSpacing.Space16))
            }
            Spacer(Modifier.height(MorningFlowSpacing.Space16))
        }

        MorningFlowBottomNavigation(
            selected = MorningFlowNavigationDestination.Templates,
            onSelected = onNavigationSelected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = MorningFlowSpacing.Space12),
        )
    }
}

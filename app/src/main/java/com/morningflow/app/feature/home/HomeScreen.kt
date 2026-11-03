package com.morningflow.app.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.common.SecondaryButton
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowBottomNavigation
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowNavigationDestination
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.designsystem.theme.illustrationFrame
import com.morningflow.app.core.designsystem.time.ClockFormat
import com.morningflow.app.feature.home.component.HomeBriefing
import com.morningflow.app.feature.home.component.HomeHeader
import com.morningflow.app.feature.home.component.HomeRoutineSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.format.DateTimeFormatter


@Composable
private fun RemainingTime(data: HomeDisplayData) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    Row(
        modifier = Modifier.fillMaxWidth().height(133.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = data.remainingMinutes.toString(),
            color = colors.brandPrimary,
            style = typography.countdownDisplay,
        )
        Spacer(Modifier.width(MorningFlowSpacing.Space12))
        Row(modifier = Modifier.padding(bottom = 25.dp)) {
            Text(
                text = stringResource(R.string.home_minutes_unit),
                color = colors.textSecondary,
                style = typography.smallStrong,
            )
            Spacer(Modifier.width(MorningFlowSpacing.Space4))
            Text(
                text = stringResource(
                    R.string.home_before_time,
                    ClockFormat.format(data.readyBy),
                ),
                color = colors.textPrimary,
                style = typography.smallStrong,
            )
        }
    }
}
@Composable
private fun StandardHomeContent(
    data: HomeDisplayData,
    onStart: (() -> Unit)?,
) {
    Column(modifier = Modifier.padding(horizontal = MorningFlowSpacing.Space32)) {
        Spacer(Modifier.height(MorningFlowSpacing.Space24))
        RemainingTime(data)
        Spacer(Modifier.height(MorningFlowSpacing.Space16))
        HomeRoutineSection(data = data, onStart = onStart)
    }
}

@Composable
private fun OutsideWindowContent(
    data: HomeDisplayData,
    onStart: (() -> Unit)?,
) {
    Column(modifier = Modifier.padding(horizontal = MorningFlowSpacing.Space32)) {
        data.illustration?.let { illustration ->
            Image(
                painter = painterResource(illustration),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(326f / 161f).illustrationFrame(),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(MorningFlowSpacing.Space12))
        // One quiet line explains why this differs from the morning screen.
        Text(
            text = stringResource(R.string.home_off_window_body, ClockFormat.format(data.earliestWakeUp)),
            modifier = Modifier.fillMaxWidth(),
            color = MorningFlowDesignSystem.colors.textSecondary,
            textAlign = TextAlign.Center,
            style = MorningFlowDesignSystem.typography.body,
        )
        HomeRoutineSection(data = data, onStart = onStart)
    }
}

@Composable
private fun InsufficientTimeContent(
    data: HomeDisplayData,
    onOpenTemplates: () -> Unit,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    Spacer(Modifier.height(48.dp))
    data.illustration?.let { illustration ->
        Image(
            painter = painterResource(illustration),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).illustrationFrame(),
            contentScale = ContentScale.Fit,
        )
    }
    Column(
        modifier = Modifier.padding(
            start = MorningFlowSpacing.Space32,
            top = 35.dp,
            end = MorningFlowSpacing.Space32,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_insufficient_title),
            modifier = Modifier.fillMaxWidth(),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            style = typography.moduleTitle,
        )
        Spacer(Modifier.height(MorningFlowSpacing.Space24))
        Text(
            text = stringResource(R.string.home_insufficient_guidance),
            modifier = Modifier.fillMaxWidth(),
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            style = typography.statusBadge,
        )
        Spacer(Modifier.height(MorningFlowSpacing.Space24))
        SecondaryButton(
            label = stringResource(R.string.home_browse_templates),
            onClick = onOpenTemplates,
        )
    }
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onStart: (selectedTemplateId: String) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSettingsPermissions: () -> Unit,
    onNavigationSelected: (MorningFlowNavigationDestination) -> Unit,
    onRefreshTime: () -> Unit,
    onRefreshCalendar: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnRefreshTime by rememberUpdatedState(onRefreshTime)
    val currentOnRefreshCalendar by rememberUpdatedState(onRefreshCalendar)

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            currentOnRefreshCalendar()
            currentOnRefreshTime()
            while (isActive) {
                delay(1_000)
                currentOnRefreshTime()
            }
        }
    }

    val colors = MorningFlowDesignSystem.colors
    val start: (() -> Unit)? = if (uiState.canStart) {
        {
        val selectedTemplateId = uiState.selectedTemplateId
        if (selectedTemplateId != null) {
            onStart(selectedTemplateId)
        }
        }
    } else {
        null
    }
    val selectNavigation: (MorningFlowNavigationDestination) -> Unit = { destination ->
        when (destination) {
            MorningFlowNavigationDestination.Templates -> onOpenTemplates()
            MorningFlowNavigationDestination.Settings -> onOpenSettings()
            MorningFlowNavigationDestination.Home,
            MorningFlowNavigationDestination.History,
            -> onNavigationSelected(destination)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = MorningFlowSpacing.Space32,
                    top = MorningFlowSpacing.Space32,
                    end = MorningFlowSpacing.Space32,
                ),
            ) {
                HomeHeader()
                Spacer(Modifier.height(MorningFlowSpacing.Space24))
                HomeBriefing(
                    state = uiState,
                    onOpenSettingsPermissions = onOpenSettingsPermissions,
                )
            }

            when (uiState.timeState) {
                HomeTimeState.OFF_WINDOW -> OutsideWindowContent(uiState.displayData, start)
                HomeTimeState.INSUFFICIENT_TIME -> InsufficientTimeContent(
                    uiState.displayData,
                    onOpenTemplates,
                )
                HomeTimeState.NORMAL -> StandardHomeContent(uiState.displayData, start)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = MorningFlowSpacing.Space8, bottom = MorningFlowSpacing.Space12),
            contentAlignment = Alignment.Center,
        ) {
            MorningFlowBottomNavigation(
                selected = uiState.displayData.selectedNavigation,
                onSelected = selectNavigation,
            )
        }
    }
}

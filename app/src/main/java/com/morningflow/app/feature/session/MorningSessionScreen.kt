package com.morningflow.app.feature.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.common.ConfirmationDialog
import com.morningflow.app.core.designsystem.component.common.PrimaryButton
import com.morningflow.app.core.designsystem.component.session.SessionControlBar
import com.morningflow.app.core.designsystem.component.session.SessionPlaybackState
import com.morningflow.app.core.designsystem.component.session.SessionStageTopBar
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.feature.session.component.LeaveBedContent
import com.morningflow.app.feature.session.component.PhysicalWakeUpContent
import com.morningflow.app.feature.session.component.RuntimeLeavingReminderContent
import com.morningflow.app.feature.session.component.SessionGuidanceContent
import com.morningflow.app.feature.session.component.resolveText

@Composable
fun MorningSessionScreen(
    data: MorningSessionDisplayData,
    onClose: () -> Unit,
    onPlaybackClick: () -> Unit,
    onComplete: () -> Unit,
    onConfirmExit: () -> Unit,
    onDismissDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val isLeaving = data.stageContentType == SessionStageContentType.LeavingReminder
    val view = LocalView.current

    // The routine is followed hands-free from across the room, so the screen must not sleep.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val haptics = LocalHapticFeedback.current
    // A light tick for every counted squat or step, a firmer one when the step changes.
    val countValue = data.countText?.formatArgs?.firstOrNull()
    var lastCount by remember { mutableStateOf(countValue) }
    LaunchedEffect(countValue) {
        if (countValue != null && lastCount != null && countValue != lastCount) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastCount = countValue
    }
    var lastStep by remember { mutableStateOf(data.heading to data.stageTitle) }
    LaunchedEffect(data.heading, data.stageTitle) {
        val step = data.heading to data.stageTitle
        if (step != lastStep) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        lastStep = step
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = if (isLeaving) 128.dp else 0.dp)) {
            Spacer(Modifier.height(MorningFlowSpacing.Space32))
            SessionStageTopBar(
                step = data.stepText.resolveText(),
                title = data.stageTitle.resolveText(),
                progress = data.sessionProgress,
                modifier = Modifier.padding(horizontal = MorningFlowSpacing.Space32),
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            // Timer ticks re-render in place; only a new step slides the content across.
            AnimatedContent(
                targetState = data,
                contentKey = { it.stageContentType to it.heading to it.illustration },
                transitionSpec = {
                    (fadeIn(tween(300, delayMillis = 90)) + slideInHorizontally(tween(300)) { it / 6 })
                        .togetherWith(fadeOut(tween(90)))
                },
                modifier = Modifier.weight(1f),
                label = "sessionStep",
            ) { step ->
                val contentModifier = Modifier.fillMaxSize()
                when (step.stageContentType) {
                    SessionStageContentType.LeaveBed -> LeaveBedContent(step, contentModifier)
                    SessionStageContentType.PhysicalWakeUp -> PhysicalWakeUpContent(step, contentModifier)
                    SessionStageContentType.Routine -> SessionGuidanceContent(step, contentModifier)
                    SessionStageContentType.LeavingReminder -> RuntimeLeavingReminderContent(
                        data = step,
                        modifier = contentModifier,
                    )
                }
            }
            data.availabilityMessages.forEach { availability ->
                Text(
                    text = availability.message.resolveText(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                    color = colors.warning,
                    style = MorningFlowDesignSystem.typography.statusBadge.copy(
                        fontSize = 18.sp, lineHeight = 24.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
            // The control bar sits in the column, not over it, so content can never run under it.
            if (data.isControlBarVisible && !isLeaving) {
                SessionStatusLine(data)
                Spacer(Modifier.height(MorningFlowSpacing.Space12))
                SessionControlBar(
                    playbackState = data.controlState,
                    onDismiss = onClose,
                    onPlaybackClick = onPlaybackClick,
                    onComplete = onComplete,
                )
                Spacer(Modifier.height(MorningFlowSpacing.Space32))
            }
        }

        if (isLeaving && data.isControlBarVisible) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(colors.background)
                    .padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton(
                    label = stringResource(R.string.session_finish),
                    onClick = onComplete,
                    enabled = data.controlState == SessionPlaybackState.Playing && !data.isDialogVisible,
                )
            }
        }

        if (data.isDialogVisible) {
            data.dialogCopy?.let { dialog ->
                ConfirmationDialog(
                    title = dialog.title.resolveText(),
                    description = dialog.description?.resolveText(),
                    confirmLabel = dialog.confirmLabel.resolveText(),
                    cancelLabel = dialog.cancelLabel.resolveText(),
                    onConfirm = onConfirmExit,
                    onCancel = onDismissDialog,
                )
            }
        }
    }
}

/** "Paused" while paused; otherwise an empty line of the same height so nothing jumps. */
@Composable
private fun SessionStatusLine(data: MorningSessionDisplayData) {
    val colors = MorningFlowDesignSystem.colors
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (data.controlState == SessionPlaybackState.Paused) {
            Text(
                text = stringResource(R.string.session_paused),
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .clip(MorningFlowShapes.Full)
                    .background(colors.warning.copy(alpha = 0.12f))
                    .padding(horizontal = MorningFlowSpacing.Space12, vertical = MorningFlowSpacing.Space4),
                color = colors.warning,
                style = MorningFlowDesignSystem.typography.smallStrong,
            )
        }
    }
}

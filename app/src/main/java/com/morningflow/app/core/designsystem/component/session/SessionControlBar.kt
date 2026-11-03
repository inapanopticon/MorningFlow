package com.morningflow.app.core.designsystem.component.session

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.MorningFlowComponentPreview
import com.morningflow.app.core.designsystem.icon.MorningFlowIcons
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing

enum class SessionPlaybackState {
    Playing,
    Paused,
}

/**
 * End, pause/resume and done. Pause/resume is the large filled button in the middle, so
 * the main action reads at a glance; the three keep fixed slots so nothing shifts when
 * the state changes. Labels are spoken by accessibility services, not printed.
 */
@Composable
fun SessionControlBar(
    playbackState: SessionPlaybackState,
    onDismiss: () -> Unit,
    onPlaybackClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val haptics = LocalHapticFeedback.current
    val isPlaying = playbackState == SessionPlaybackState.Playing

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = MorningFlowSpacing.Space32),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        SessionControl(
            icon = MorningFlowIcons.Close,
            label = stringResource(R.string.session_control_end),
            buttonSize = 56.dp,
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
            borderColor = colors.borderSubtle,
            onClick = onDismiss,
        )
        SessionControl(
            icon = if (isPlaying) MorningFlowIcons.Pause else MorningFlowIcons.Play,
            label = stringResource(
                if (isPlaying) R.string.session_control_pause else R.string.session_control_resume,
            ),
            buttonSize = 72.dp,
            containerColor = colors.brandPrimary,
            contentColor = colors.surface,
            borderColor = colors.brandPrimary,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onPlaybackClick()
            },
        )
        SessionControl(
            icon = MorningFlowIcons.Check,
            label = stringResource(R.string.session_control_done),
            buttonSize = 56.dp,
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
            borderColor = colors.borderSubtle,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onComplete()
            },
        )
    }
}

@Composable
private fun SessionControl(
    @DrawableRes icon: Int,
    label: String,
    buttonSize: Dp,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(min = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.height(72.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(containerColor)
                    .border(1.dp, borderColor, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor,
                )
            }
        }
    }
}

@Preview(
    name = "Session Control Bar Variants",
    showBackground = true,
)
@Composable
private fun SessionControlBarPreview() {
    MorningFlowComponentPreview {
        SessionControlBar(
            playbackState = SessionPlaybackState.Playing,
            onDismiss = {},
            onPlaybackClick = {},
            onComplete = {},
        )

        SessionControlBar(
            playbackState = SessionPlaybackState.Paused,
            onDismiss = {},
            onPlaybackClick = {},
            onComplete = {},
        )
    }
}

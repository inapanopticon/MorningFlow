package com.morningflow.app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.designsystem.theme.MorningFlowTheme

internal object ComponentPreviewData {
    const val Action = "Save"
    const val BackTitle = "Template"
    const val BehaviorLabel = "Brush Teeth"
    const val BehaviorValue = "S · 2 min"
    const val Body = "Your current progress will not be saved."
    const val Cancel = "Keep Going"
    const val Confirm = "End Session"
    const val Day = "Mon"
    const val Duration = "5 min"
    const val DialogTitle = "End your morning session?"
    const val Error = "Template name already exists."
    const val FieldLabel = "Template name *"
    const val FieldValue = "My morning"
    const val PrimaryAction = "Start"
    const val RoutineDuration = "2 m"
    const val RoutineTitle = "Get out of bed"
    const val Sequence = "01"
    const val SessionStep = "01 / 08"
    const val SessionTitle = "Leave Bed"
    const val StageDuration = "3m"
    const val StageTitle = "Leave Bed"
    const val VoiceUnavailable = "Voice guidance unavailable"
    const val StepSensorUnavailable = "Step sensor unavailable"
    const val LightSensorUnavailable = "Light sensor unavailable"
    const val ModuleBehaviorLabel = "Meal Prep"
    const val ModuleBehaviorValue = "S · 5 min"
    const val Time = "07 : 30"
    const val TimeLabel = "Earliest wake-up"
    const val Title = "Meal Preparation"
}

@Composable
internal fun MorningFlowComponentPreview(content: @Composable ColumnScope.() -> Unit) {
    MorningFlowTheme {
        Surface(color = MorningFlowDesignSystem.colors.background) {
            Column(
                modifier = Modifier.padding(MorningFlowSpacing.Space24),
                verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space16),
                content = content,
            )
        }
    }
}

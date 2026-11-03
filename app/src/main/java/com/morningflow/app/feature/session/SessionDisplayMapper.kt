package com.morningflow.app.feature.session

import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.session.AvailabilityType
import com.morningflow.app.core.designsystem.component.session.SessionPlaybackState
import com.morningflow.app.core.designsystem.illustration.MorningFlowIllustrations
import com.morningflow.app.core.domain.runtime.SessionRuntimeState
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.SessionStatus
import com.morningflow.app.core.model.SessionUiState

/**
 * Turns the running session into what [MorningSessionScreen] already knows how to draw.
 *
 * The screen is deliberately kept stateless, so everything it needs - which stage, which
 * artwork, what the clock reads - is derived here from the engine's state rather than
 * held in the UI.
 */
object SessionDisplayMapper {

    fun map(state: SessionUiState, isDialogVisible: Boolean, runtime: SessionRuntimeState? = null): MorningSessionDisplayData? {
        val module = state.currentModule ?: return null
        val behaviour = state.currentBehaviour
        val key = behaviour?.key

        return MorningSessionDisplayData(
            stageContentType = stageContentType(module.type),
            stepText = SessionDisplayText(
                R.string.session_step_format,
                listOf(state.currentModuleIndex + 1, state.modules.size),
            ),
            stageTitle = SessionDisplayText(stageTitle(module.type)),
            heading = SessionDisplayText(heading(key, module.type)),
            guidanceBody = SessionDisplayText(guidance(key, module.type)),
            timerText = if (countText(state, module.type, key) == null) timerText(state) else null,
            countText = countText(state, module.type, key),
            illustration = illustration(key, module.type),
            availabilityMessages = if (key in setOf(BehaviourKey.WALKING, BehaviourKey.SQUATS) &&
                runtime != null && runtime.current?.behaviour?.key == key && runtime.status == state.status &&
                !runtime.sensorAvailable
            ) listOf(SessionAvailabilityDisplayData(AvailabilityType.StepSensor,
                SessionDisplayText(R.string.session_sensor_unavailable))) else emptyList(),
            controlState = when (state.status) {
                SessionStatus.PAUSED -> SessionPlaybackState.Paused
                else -> SessionPlaybackState.Playing
            },
            leavingReminder = if (module.type == ModuleType.LEAVING_REMINDER) LeavingReminderPresenter.loading() else null,
            isDialogVisible = isDialogVisible,
            dialogCopy = if (isDialogVisible) EXIT_DIALOG else null,
            sessionProgress = sessionProgress(state),
        )
    }

    private fun sessionProgress(state: SessionUiState): Float {
        if (state.modules.isEmpty()) return 0f
        val behaviours = state.currentModule?.behaviours?.count { it.enabled } ?: 0
        val withinStage = if (behaviours == 0) 0f else state.currentBehaviourIndex.toFloat() / behaviours
        return ((state.currentModuleIndex + withinStage) / state.modules.size).coerceIn(0f, 1f)
    }

    private fun timerText(state: SessionUiState): SessionDisplayText = SessionDisplayText(
        R.string.session_time_format,
        listOf(state.remainingSeconds / 60, state.remainingSeconds % 60),
    )

    /** Squats and walking count progress; every other step just runs its clock. */
    private fun countText(
        state: SessionUiState,
        module: ModuleType,
        key: BehaviourKey?,
    ): SessionDisplayText? {
        if (module != ModuleType.PHYSICAL_WAKE_UP) return null
        val target = state.currentModule ?: return null
        return when (key) {
            BehaviourKey.SQUATS -> SessionDisplayText(
                R.string.session_count_format,
                listOf(state.sensor.squatCount.coerceIn(0, target.squatTarget.coerceAtLeast(0)), target.squatTarget),
            )
            BehaviourKey.WALKING -> SessionDisplayText(
                R.string.session_count_format,
                listOf(state.sensor.stepCount.coerceIn(0, target.stepTarget.coerceAtLeast(0)), target.stepTarget),
            )
            else -> null
        }
    }

    private fun stageContentType(module: ModuleType): SessionStageContentType = when (module) {
        ModuleType.LEAVE_BED -> SessionStageContentType.LeaveBed
        ModuleType.PHYSICAL_WAKE_UP -> SessionStageContentType.PhysicalWakeUp
        ModuleType.LEAVING_REMINDER -> SessionStageContentType.LeavingReminder
        else -> SessionStageContentType.Routine
    }

    private fun stageTitle(module: ModuleType): Int = when (module) {
        ModuleType.LEAVE_BED -> R.string.session_stage_leave_bed
        ModuleType.PHYSICAL_WAKE_UP -> R.string.session_stage_physical_wakeup
        ModuleType.MORNING_RESET -> R.string.session_stage_morning_reset
        ModuleType.MEAL_PREPARATION -> R.string.session_stage_meal_preparation
        ModuleType.WASH_AND_GROOM -> R.string.session_stage_wash_groom
        ModuleType.BREAKFAST -> R.string.session_stage_breakfast
        ModuleType.GET_READY -> R.string.session_stage_get_ready
        ModuleType.LEAVING_REMINDER -> R.string.session_stage_leaving_reminder
    }

    /**
     * Stages whose steps are visibly different - getting out of bed, the warm-up - name
     * the step; the rest keep one heading for the whole stage.
     */
    private fun heading(key: BehaviourKey?, module: ModuleType): Int = when (key) {
        BehaviourKey.LEAVE_BED -> R.string.session_heading_good_morning
        BehaviourKey.CHANGE_INTO_HOME_CLOTHES, BehaviourKey.LIGHT_REMINDER ->
            R.string.session_heading_get_comfy
        BehaviourKey.STRETCHING -> R.string.session_heading_stretching
        BehaviourKey.SQUATS -> R.string.session_heading_squats
        BehaviourKey.WALKING -> R.string.session_heading_walking
        else -> when (module) {
            ModuleType.MORNING_RESET -> R.string.session_heading_morning_reset
            ModuleType.MEAL_PREPARATION -> R.string.session_heading_prepare_breakfast
            ModuleType.WASH_AND_GROOM -> R.string.session_heading_wash_and_groom
            ModuleType.BREAKFAST -> R.string.session_heading_breakfast_time
            else -> R.string.session_heading_get_ready
        }
    }

    /** One line on screen per step, saying the same thing the voice does. */
    private fun guidance(key: BehaviourKey?, module: ModuleType): Int = when (key) {
        BehaviourKey.LEAVE_BED -> R.string.session_guidance_leave_bed
        BehaviourKey.CHANGE_INTO_HOME_CLOTHES -> R.string.session_guidance_get_comfy
        BehaviourKey.LIGHT_REMINDER -> R.string.session_guidance_light_reminder
        BehaviourKey.STRETCHING -> R.string.session_guidance_stretching
        BehaviourKey.SQUATS -> R.string.session_guidance_squats
        BehaviourKey.WALKING -> R.string.session_guidance_walking
        BehaviourKey.TURN_ON_LIGHT -> R.string.session_guidance_turn_on_light
        BehaviourKey.OPEN_CURTAINS -> R.string.session_guidance_open_curtains
        BehaviourKey.MAKE_BED -> R.string.session_guidance_make_bed
        BehaviourKey.TIDY_ROOM -> R.string.session_guidance_tidy_room
        BehaviourKey.BRUSH_TEETH -> R.string.session_guidance_brush_teeth
        BehaviourKey.WASH_FACE -> R.string.session_guidance_wash_face
        BehaviourKey.SKINCARE -> R.string.session_guidance_skincare
        BehaviourKey.CHANGE_CLOTHES -> R.string.session_guidance_change_clothes
        BehaviourKey.MAKEUP -> R.string.session_guidance_makeup
        BehaviourKey.OTHER_PERSONAL_PREPARATION -> R.string.session_guidance_other_preparation
        else -> when (module) {
            ModuleType.MORNING_RESET -> R.string.session_guidance_morning_reset
            ModuleType.MEAL_PREPARATION -> R.string.session_guidance_meal_preparation
            ModuleType.WASH_AND_GROOM -> R.string.session_guidance_wash_groom
            ModuleType.BREAKFAST -> R.string.session_guidance_breakfast
            else -> R.string.session_guidance_get_ready
        }
    }

    private fun illustration(key: BehaviourKey?, module: ModuleType): Int = when (key) {
        BehaviourKey.LEAVE_BED -> MorningFlowIllustrations.LeaveBedStart
        BehaviourKey.CHANGE_INTO_HOME_CLOTHES -> MorningFlowIllustrations.LeaveBedActive
        BehaviourKey.LIGHT_REMINDER -> MorningFlowIllustrations.LeaveBedLightReminder
        BehaviourKey.STRETCHING -> MorningFlowIllustrations.PhysicalWakeupStretching
        BehaviourKey.SQUATS -> MorningFlowIllustrations.PhysicalWakeupSquats
        BehaviourKey.WALKING -> MorningFlowIllustrations.PhysicalWakeupWalking
        else -> when (module) {
            ModuleType.MORNING_RESET -> MorningFlowIllustrations.MorningReset
            ModuleType.MEAL_PREPARATION -> MorningFlowIllustrations.MealPreparation
            ModuleType.WASH_AND_GROOM -> MorningFlowIllustrations.WashGroom
            ModuleType.BREAKFAST -> MorningFlowIllustrations.Breakfast
            ModuleType.LEAVING_REMINDER -> MorningFlowIllustrations.LeavingReminder
            else -> MorningFlowIllustrations.GetReady
        }
    }

    private val EXIT_DIALOG = SessionDialogDisplayData(
        title = SessionDisplayText(R.string.session_exit_title),
        confirmLabel = SessionDisplayText(R.string.session_exit_confirm),
        cancelLabel = SessionDisplayText(R.string.session_exit_cancel),
    )
}

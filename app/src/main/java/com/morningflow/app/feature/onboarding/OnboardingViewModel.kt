package com.morningflow.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningflow.app.core.data.SettingsRepository
import com.morningflow.app.core.data.TemplateRepository
import com.morningflow.app.core.data.notification.api.NotificationAvailability
import com.morningflow.app.core.model.GlobalMorningSchedule
import com.morningflow.app.core.model.OnboardingStep
import com.morningflow.app.core.model.RECOMMENDED_TEMPLATE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

private val SuggestedSchedule = GlobalMorningSchedule(
    earliestWakeUp = LocalTime.of(7, 0),
    latestWakeUp = LocalTime.of(7, 30),
    readyBy = LocalTime.of(8, 30),
)
private val SuggestedWeekdays = DayOfWeek.entries.take(5).toSet()

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val isReady: Boolean = false,
    val earliestWakeUp: LocalTime? = null,
    val latestWakeUp: LocalTime? = null,
    val readyBy: LocalTime? = null,
    val activeDays: Set<DayOfWeek> = emptySet(),
    val errors: Set<OnboardingField> = emptySet(),
    val isBeginning: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val locationPermission: PermissionAccess = PermissionAccess.NotAvailable,
    val calendarPermission: PermissionAccess = PermissionAccess.NotAvailable,
    val sensorsPermission: PermissionAccess = PermissionAccess.NotAvailable,
    val notificationPermission: PermissionAccess = PermissionAccess.NotAvailable,
    val exactAlarmPermission: PermissionAccess = PermissionAccess.NotAvailable,
)

/** Owns the resumable Welcome and Wake-up Setup flow without persisting draft input. */
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val templateRepository: TemplateRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope
    private val _uiState = MutableStateFlow(
        OnboardingUiState(),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        coroutineScope.launch {
            settingsRepository.awaitReady()
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    step = settings.onboardingStep,
                    isReady = true,
                )
            }
        }
    }

    fun begin() {
        if (!_uiState.value.isReady ||
            _uiState.value.step != OnboardingStep.WELCOME ||
            _uiState.value.isBeginning
        ) return
        // Start from a typical weekday morning rather than blank fields; anything the user
        // already chose is kept.
        _uiState.value = _uiState.value.run {
            copy(
                isBeginning = true,
                saveFailed = false,
                earliestWakeUp = earliestWakeUp ?: SuggestedSchedule.earliestWakeUp,
                latestWakeUp = latestWakeUp ?: SuggestedSchedule.latestWakeUp,
                readyBy = readyBy ?: SuggestedSchedule.readyBy,
                activeDays = activeDays.ifEmpty { SuggestedWeekdays },
            )
        }
        persistStep(OnboardingStep.WAKE_UP_SETUP)
    }

    fun setEarliestWakeUp(value: LocalTime?) = updateDraft { copy(earliestWakeUp = value) }

    fun setLatestWakeUp(value: LocalTime?) = updateDraft { copy(latestWakeUp = value) }

    fun setReadyBy(value: LocalTime?) = updateDraft { copy(readyBy = value) }

    fun toggleActiveDay(day: DayOfWeek) = updateDraft {
        copy(activeDays = if (day in activeDays) activeDays - day else activeDays + day)
    }

    fun refreshPermissionState(
        locationGranted: Boolean,
        calendarGranted: Boolean,
        sensorsGranted: Boolean? = null,
        notificationAvailability: NotificationAvailability? = null,
        exactAlarmGranted: Boolean? = null,
    ) {
        _uiState.value = _uiState.value.copy(
            locationPermission = locationGranted.toPermissionAccess(),
            calendarPermission = calendarGranted.toPermissionAccess(),
            sensorsPermission = sensorsGranted.toPermissionAccess(),
            notificationPermission = notificationAvailability.toPermissionAccess(),
            exactAlarmPermission = exactAlarmGranted.toPermissionAccess(),
        )
    }

    /** Completes the permission step while preserving SettingsRepository as the source of truth. */
    fun completePermissions() {
        if (!_uiState.value.isReady) return
        // The permission route is rendered from the same state flow, but the outer App
        // and the route collect it independently. During DataStore restoration their
        // cached snapshots can briefly differ. The persisted step is authoritative for
        // this action, so do not silently drop a valid NEXT click in that window.
        if (_uiState.value.step != OnboardingStep.PERMISSIONS &&
            settingsRepository.settings.value.onboardingStep != OnboardingStep.PERMISSIONS
        ) return
        persistStep(OnboardingStep.COMPLETE)
    }

    fun next() {
        val current = _uiState.value
        if (!current.isReady || current.step != OnboardingStep.WAKE_UP_SETUP || current.isSaving) return

        val errors = validate(current)
        if (errors.isNotEmpty()) {
            _uiState.value = current.copy(errors = errors, saveFailed = false)
            return
        }

        val schedule = GlobalMorningSchedule(
            earliestWakeUp = current.earliestWakeUp!!,
            latestWakeUp = current.latestWakeUp!!,
            readyBy = current.readyBy!!,
        )
        _uiState.value = current.copy(errors = emptySet(), isSaving = true, saveFailed = false)
        coroutineScope.launch {
            try {
                // Keep the existing system template and its formal module/behaviour defaults.
                templateRepository.awaitReady()
                val recommended = templateRepository.templates.value
                    .singleOrNull { it.id == RECOMMENDED_TEMPLATE_ID }
                    ?: error("Recommended Morning is missing from TemplateRepository")

                settingsRepository.update { it.copy(schedule = schedule) }
                templateRepository.save(
                    recommended.copy(
                        applicableDays = current.activeDays,
                        applicableStart = current.earliestWakeUp,
                        priority = 5,
                    ),
                )
                templateRepository.setDefault(RECOMMENDED_TEMPLATE_ID)
                settingsRepository.update { it.copy(onboardingStep = OnboardingStep.PERMISSIONS) }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(isSaving = false, saveFailed = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                step = OnboardingStep.PERMISSIONS,
                isSaving = false,
                saveFailed = false,
            )
        }
    }

    private fun persistStep(step: OnboardingStep) {
        coroutineScope.launch {
            try {
                settingsRepository.update { it.copy(onboardingStep = step) }
                _uiState.value = _uiState.value.copy(
                    step = step,
                    isBeginning = false,
                    saveFailed = false,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(isBeginning = false, saveFailed = true)
            }
        }
    }

    private fun updateDraft(update: OnboardingUiState.() -> OnboardingUiState) {
        if (_uiState.value.isBeginning || _uiState.value.isSaving) return
        _uiState.value = _uiState.value.update().copy(errors = emptySet(), saveFailed = false)
    }

    private fun validate(state: OnboardingUiState): Set<OnboardingField> = buildSet {
        if (state.earliestWakeUp == null) add(OnboardingField.EARLIEST_WAKE_UP)
        if (state.latestWakeUp == null) add(OnboardingField.LATEST_WAKE_UP)
        if (state.readyBy == null) add(OnboardingField.READY_BY)
        if (state.activeDays.isEmpty()) add(OnboardingField.ACTIVE_DAYS)

        val earliest = state.earliestWakeUp
        val latest = state.latestWakeUp
        val readyBy = state.readyBy
        if (earliest != null && latest != null && !earliest.isBefore(latest)) {
            add(OnboardingField.LATEST_WAKE_UP)
        }
        if (latest != null && readyBy != null && !latest.isBefore(readyBy)) {
            add(OnboardingField.READY_BY)
        }
    }

    private fun Boolean?.toPermissionAccess(): PermissionAccess = when (this) {
        true -> PermissionAccess.Granted
        false -> PermissionAccess.Denied
        null -> PermissionAccess.NotAvailable
    }

    private fun NotificationAvailability?.toPermissionAccess(): PermissionAccess = when (this) {
        NotificationAvailability.AVAILABLE -> PermissionAccess.Granted
        NotificationAvailability.RUNTIME_PERMISSION_DENIED,
        NotificationAvailability.APP_NOTIFICATIONS_DISABLED,
        NotificationAvailability.CHANNEL_DISABLED,
        -> PermissionAccess.Denied
        null -> PermissionAccess.NotAvailable
    }
}

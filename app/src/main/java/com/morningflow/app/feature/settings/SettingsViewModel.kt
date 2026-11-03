package com.morningflow.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningflow.app.R
import com.morningflow.app.core.data.SettingsRepository
import com.morningflow.app.core.data.WakeupScheduleFailureReason
import com.morningflow.app.core.data.WakeupScheduleStatus
import com.morningflow.app.core.data.notification.api.NotificationAvailability
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowNavigationDestination
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.Appearance
import com.morningflow.app.core.model.GlobalMorningSchedule
import com.morningflow.app.core.model.ReminderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private data class SettingsPermissionState(
    val locationGranted: Boolean? = null,
    val calendarGranted: Boolean? = null,
    val sensorsGranted: Boolean? = null,
    val notificationAvailability: NotificationAvailability? = null,
    val exactAlarmGranted: Boolean? = null,
)

private data class ScheduleEditState(
    val draft: GlobalMorningSchedule? = null,
    val errors: Set<SettingsTimeField> = emptySet(),
    val isReady: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: SettingsScheduleError? = null,
)

internal const val MAX_THING_LENGTH = 40

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope? = null,
    private val wakeupScheduleStatus: StateFlow<WakeupScheduleStatus> =
        MutableStateFlow(WakeupScheduleStatus.NotScheduled),
) : ViewModel() {
    private val stateScope = scope ?: viewModelScope
    private val permissionState = MutableStateFlow(SettingsPermissionState())
    private val scheduleEditState = MutableStateFlow(ScheduleEditState())
    private val _uiState = MutableStateFlow(
        settingsRepository.settings.value.toDisplayData(
            permissionState = permissionState.value,
            wakeupScheduleStatus = wakeupScheduleStatus.value,
        ),
    )
    val uiState: StateFlow<SettingsDisplayData> = _uiState.asStateFlow()

    init {
        stateScope.launch {
            try {
                settingsRepository.awaitReady()
                scheduleEditState.value = scheduleEditState.value.copy(isReady = true, error = null)
                rebuildDisplayData()
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                scheduleEditState.value = scheduleEditState.value.copy(
                    isLoading = false,
                    error = SettingsScheduleError.ReadinessFailed,
                )
                rebuildDisplayData()
            }
        }
        stateScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = settings.toDisplayData(
                    permissionState.value,
                    scheduleEditState.value,
                    wakeupScheduleStatus.value,
                )
            }
        }
        stateScope.launch {
            permissionState.collect { permissions ->
                _uiState.value = settingsRepository.settings.value.toDisplayData(
                    permissions,
                    scheduleEditState.value,
                    wakeupScheduleStatus.value,
                )
            }
        }
        stateScope.launch {
            scheduleEditState.collect { edit ->
                _uiState.value = settingsRepository.settings.value.toDisplayData(
                    permissionState.value,
                    edit,
                    wakeupScheduleStatus.value,
                )
            }
        }
        stateScope.launch {
            wakeupScheduleStatus.collect {
                rebuildDisplayData()
            }
        }
    }

    fun refreshPermissionState(
        locationGranted: Boolean,
        calendarGranted: Boolean,
        sensorsGranted: Boolean? = null,
        notificationAvailability: NotificationAvailability? = null,
        exactAlarmGranted: Boolean? = null,
    ) {
        permissionState.value = SettingsPermissionState(
            locationGranted = locationGranted,
            calendarGranted = calendarGranted,
            sensorsGranted = sensorsGranted,
            notificationAvailability = notificationAvailability,
            exactAlarmGranted = exactAlarmGranted,
        )
    }

    fun beginScheduleEdit(onReady: (GlobalMorningSchedule) -> Unit = {}) {
        val current = scheduleEditState.value
        current.draft?.let {
            onReady(it)
            return
        }
        if (current.isLoading) return
        scheduleEditState.value = current.copy(isLoading = true, error = null)
        rebuildDisplayData()
        stateScope.launch {
            try {
                settingsRepository.awaitReady()
                val schedule = settingsRepository.settings.value.schedule
                scheduleEditState.value = scheduleEditState.value.copy(
                    draft = schedule,
                    isReady = true,
                    isLoading = false,
                    error = null,
                )
                rebuildDisplayData()
                onReady(schedule)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                scheduleEditState.value = scheduleEditState.value.copy(
                    isLoading = false,
                    error = SettingsScheduleError.ReadinessFailed,
                )
                rebuildDisplayData()
            }
        }
    }

    fun updateScheduleTime(field: SettingsTimeField, value: java.time.LocalTime) {
        val current = scheduleEditState.value.draft ?: return
        val updated = when (field) {
            SettingsTimeField.EarliestWakeUp -> current.copy(earliestWakeUp = value)
            SettingsTimeField.LatestWakeUp -> current.copy(latestWakeUp = value)
            SettingsTimeField.ReadyBy -> current.copy(readyBy = value)
        }
        scheduleEditState.value = scheduleEditState.value.copy(
            draft = updated,
            errors = emptySet(),
            error = null,
        )
        rebuildDisplayData()
    }

    fun cancelScheduleEdit() {
        scheduleEditState.value = ScheduleEditState(isReady = scheduleEditState.value.isReady)
        rebuildDisplayData()
    }

    fun saveSchedule() {
        val current = scheduleEditState.value
        val draft = current.draft ?: return
        val errors = validateSchedule(draft)
        if (errors.isNotEmpty()) {
            scheduleEditState.value = current.copy(errors = errors, error = null)
            rebuildDisplayData()
            return
        }
        if (current.isSaving) return
        scheduleEditState.value = current.copy(isSaving = true, error = null)
        rebuildDisplayData()
        stateScope.launch {
            try {
                settingsRepository.awaitReady()
                settingsRepository.update { it.copy(schedule = draft) }
                scheduleEditState.value = scheduleEditState.value.copy(
                    draft = null,
                    errors = emptySet(),
                    isSaving = false,
                    error = null,
                )
                rebuildDisplayData()
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                scheduleEditState.value = scheduleEditState.value.copy(
                    isSaving = false,
                    error = SettingsScheduleError.SaveFailed,
                )
                rebuildDisplayData()
            }
        }
    }

    fun toggle(key: SettingsToggleKey) {
        when (key) {
            SettingsToggleKey.BackgroundMusic -> update {
                copy(audio = audio.copy(backgroundMusicEnabled = !audio.backgroundMusicEnabled))
            }
            SettingsToggleKey.AudioCues -> update {
                copy(audio = audio.copy(audioCuesEnabled = !audio.audioCuesEnabled))
            }
            SettingsToggleKey.VoiceGuidance -> update {
                copy(audio = audio.copy(voiceGuidanceEnabled = !audio.voiceGuidanceEnabled))
            }
        }
    }

    fun setVolume(key: SettingsToggleKey, value: Float) {
        val volume = value.coerceIn(0f, 1f)
        when (key) {
            SettingsToggleKey.BackgroundMusic -> update {
                copy(audio = audio.copy(backgroundMusicVolume = volume))
            }
            SettingsToggleKey.VoiceGuidance -> update {
                copy(audio = audio.copy(voiceGuidanceVolume = volume))
            }
            else -> Unit
        }
    }

    fun selectReminder(key: SettingsPreferenceKey, value: Int? = null) {
        when (key) {
            SettingsPreferenceKey.Umbrella -> update {
                if (value == null || value in ReminderPreferences.UMBRELLA_RAIN_PERCENT_OPTIONS) {
                    copy(reminders = reminders.copy(umbrellaRainPercent = value))
                } else {
                    this
                }
            }
            SettingsPreferenceKey.Jacket -> update {
                if (value == null || value in ReminderPreferences.JACKET_TEMPERATURE_OPTIONS) {
                    copy(reminders = reminders.copy(jacketTemperatureCelsius = value))
                } else {
                    this
                }
            }
            SettingsPreferenceKey.Sunscreen -> update {
                copy(reminders = reminders.copy(sunscreen = !reminders.sunscreen))
            }
            SettingsPreferenceKey.Unknown -> Unit
        }
    }

    /** Adds an item to pack; blank names and repeats (ignoring case) are ignored. */
    fun setAppearance(appearance: Appearance) {
        update { copy(appearance = appearance) }
    }

    fun addThingToBring(name: String) {
        val item = name.trim().take(MAX_THING_LENGTH)
        if (item.isEmpty()) return
        update {
            if (thingsToBring.any { it.equals(item, ignoreCase = true) }) this
            else copy(thingsToBring = thingsToBring + item)
        }
    }

    fun removeThingToBring(name: String) {
        update { copy(thingsToBring = thingsToBring.filterNot { it == name }) }
    }

    private fun update(transform: AppSettings.() -> AppSettings) {
        stateScope.launch { settingsRepository.update(transform) }
    }

    private fun rebuildDisplayData() {
        _uiState.value = settingsRepository.settings.value.toDisplayData(
            permissionState.value,
            scheduleEditState.value,
            wakeupScheduleStatus.value,
        )
    }
}

internal fun validateSchedule(schedule: GlobalMorningSchedule): Set<SettingsTimeField> = buildSet {
    if (!schedule.earliestWakeUp.isBefore(schedule.latestWakeUp)) {
        add(SettingsTimeField.EarliestWakeUp)
        add(SettingsTimeField.LatestWakeUp)
    }
    if (!schedule.latestWakeUp.isBefore(schedule.readyBy)) {
        add(SettingsTimeField.LatestWakeUp)
        add(SettingsTimeField.ReadyBy)
    }
}

private fun AppSettings.toDisplayData(
    permissionState: SettingsPermissionState,
    scheduleEditState: ScheduleEditState = ScheduleEditState(),
    wakeupScheduleStatus: WakeupScheduleStatus = WakeupScheduleStatus.NotScheduled,
): SettingsDisplayData = SettingsDisplayData(
    earliestWakeUp = scheduleEditState.draft?.earliestWakeUp ?: schedule.earliestWakeUp,
    latestWakeUp = scheduleEditState.draft?.latestWakeUp ?: schedule.latestWakeUp,
    readyBy = scheduleEditState.draft?.readyBy ?: schedule.readyBy,
    audioSettings = listOf(
        SettingsToggleDisplayData(
            key = SettingsToggleKey.BackgroundMusic,
            label = R.string.settings_background_music,
            description = R.string.settings_background_music_description,
            enabled = audio.backgroundMusicEnabled,
            volume = audio.backgroundMusicVolume,
        ),
        SettingsToggleDisplayData(
            key = SettingsToggleKey.VoiceGuidance,
            label = R.string.settings_voice_guidance,
            description = R.string.settings_voice_guidance_description,
            enabled = audio.voiceGuidanceEnabled,
            volume = audio.voiceGuidanceVolume,
        ),
        SettingsToggleDisplayData(
            key = SettingsToggleKey.AudioCues,
            label = R.string.settings_audio_cues,
            description = R.string.settings_audio_cues_description,
            enabled = audio.audioCuesEnabled,
        ),
    ),
    permissions = listOf(
        SettingsPermissionDisplayData(
            label = R.string.settings_location,
            status = permissionState.locationGranted.toPermissionStatus(),
            key = SettingsPermissionKey.Location,
        ),
        SettingsPermissionDisplayData(
            label = R.string.settings_calendar,
            status = permissionState.calendarGranted.toPermissionStatus(),
            key = SettingsPermissionKey.Calendar,
        ),
        SettingsPermissionDisplayData(
            label = R.string.settings_sensors,
            status = permissionState.sensorsGranted.toPermissionStatus(),
            supportingText = R.string.settings_sensors_used_for,
            key = SettingsPermissionKey.Sensors,
        ),
        SettingsPermissionDisplayData(
            label = R.string.settings_notifications,
            status = permissionState.notificationAvailability.toPermissionStatus(),
            key = SettingsPermissionKey.Notifications,
        ),
        SettingsPermissionDisplayData(
            label = R.string.settings_alarms_and_reminders,
            status = permissionState.exactAlarmGranted.toPermissionStatus(),
            key = SettingsPermissionKey.AlarmsAndReminders,
        ),
    ),
    reminderPreferences = listOf(
        SettingsPreferenceDisplayData(
            label = R.string.settings_umbrella_reminder,
            value = reminders.umbrellaRainPercent,
            active = reminders.umbrellaRainPercent != null,
            key = SettingsPreferenceKey.Umbrella,
        ),
        SettingsPreferenceDisplayData(
            label = R.string.settings_jacket_reminder,
            value = reminders.jacketTemperatureCelsius,
            active = reminders.jacketTemperatureCelsius != null,
            key = SettingsPreferenceKey.Jacket,
        ),
        SettingsPreferenceDisplayData(
            label = R.string.settings_sunscreen_reminder,
            active = reminders.sunscreen,
            key = SettingsPreferenceKey.Sunscreen,
        ),
    ),
    thingsToBring = thingsToBring,
    appearance = appearance,
    selectedNavigation = MorningFlowNavigationDestination.Settings,
    isScheduleReady = scheduleEditState.isReady,
    isEditingSchedule = scheduleEditState.draft != null,
    scheduleErrors = scheduleEditState.errors,
    scheduleError = scheduleEditState.error,
    schedulerWarning = wakeupScheduleStatus.toSettingsSchedulerWarning(),
)

private fun WakeupScheduleStatus.toSettingsSchedulerWarning(): SettingsSchedulerWarning? =
    when (this) {
        is WakeupScheduleStatus.Failed -> when (reason) {
            WakeupScheduleFailureReason.EXACT_ACCESS_REQUIRED ->
                SettingsSchedulerWarning.ExactAccessRequired
            WakeupScheduleFailureReason.PLATFORM_REJECTED ->
                SettingsSchedulerWarning.PlatformRejected
            WakeupScheduleFailureReason.ZERO_LENGTH_WINDOW,
            WakeupScheduleFailureReason.END_BEFORE_START,
            -> null
        }
        WakeupScheduleStatus.NotScheduled,
        WakeupScheduleStatus.Scheduled,
        -> null
    }

private fun Boolean?.toPermissionStatus(): SettingsPermissionStatus = when (this) {
    true -> SettingsPermissionStatus.Granted
    false -> SettingsPermissionStatus.Denied
    null -> SettingsPermissionStatus.NotAvailable
}

private fun NotificationAvailability?.toPermissionStatus(): SettingsPermissionStatus = when (this) {
    NotificationAvailability.AVAILABLE -> SettingsPermissionStatus.Granted
    NotificationAvailability.RUNTIME_PERMISSION_DENIED,
    NotificationAvailability.APP_NOTIFICATIONS_DISABLED,
    NotificationAvailability.CHANNEL_DISABLED,
    -> SettingsPermissionStatus.Denied
    null -> SettingsPermissionStatus.NotAvailable
}

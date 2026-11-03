package com.morningflow.app.feature.settings

import com.morningflow.app.core.data.FakeSettingsRepository
import com.morningflow.app.core.data.SettingsRepository
import com.morningflow.app.core.data.WakeupScheduleFailureReason
import com.morningflow.app.core.data.WakeupScheduleStatus
import com.morningflow.app.core.data.notification.api.NotificationAvailability
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.AudioSettings
import com.morningflow.app.core.model.GlobalMorningSchedule
import com.morningflow.app.core.model.ReminderPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Test
    fun settingsPermissionClickabilityAllowsGrantedAndDeniedButNotUnavailable() {
        assertTrue(SettingsPermissionStatus.Granted.isSettingsPermissionClickable())
        assertTrue(SettingsPermissionStatus.Denied.isSettingsPermissionClickable())
        assertFalse(SettingsPermissionStatus.NotAvailable.isSettingsPermissionClickable())

        assertTrue(
            SettingsPermissionDisplayData(
                label = 0,
                status = SettingsPermissionStatus.Granted,
                key = SettingsPermissionKey.AlarmsAndReminders,
            ).isClickable(),
        )
        assertTrue(
            SettingsPermissionDisplayData(
                label = 0,
                status = SettingsPermissionStatus.Denied,
                key = SettingsPermissionKey.Notifications,
            ).isClickable(),
        )
        assertTrue(
            SettingsPermissionDisplayData(
                label = 0,
                status = SettingsPermissionStatus.Granted,
                key = SettingsPermissionKey.Notifications,
            ).isClickable(),
        )
        assertTrue(
            SettingsPermissionDisplayData(
                label = 0,
                status = SettingsPermissionStatus.Denied,
                key = SettingsPermissionKey.AlarmsAndReminders,
            ).isClickable(),
        )
    }

    @Test
    fun scheduleEditStartsFromCurrentValuesAndDoesNotPersistChanges() = runTest {
        val repository = FakeSettingsRepository()
        val original = GlobalMorningSchedule(LocalTime.of(6, 0), LocalTime.of(7, 0), LocalTime.of(8, 0))
        repository.update { it.copy(schedule = original) }
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)

        viewModel.beginScheduleEdit()
        scope.advanceUntilIdle()
        viewModel.updateScheduleTime(SettingsTimeField.LatestWakeUp, LocalTime.of(7, 30))

        assertEquals(LocalTime.of(7, 30), viewModel.uiState.value.latestWakeUp)
        assertEquals(original, repository.settings.value.schedule)
    }

    @Test
    fun cancelScheduleEditRestoresSavedValues() = runTest {
        val repository = FakeSettingsRepository()
        val original = repository.settings.value.schedule
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)

        viewModel.beginScheduleEdit()
        scope.advanceUntilIdle()
        viewModel.updateScheduleTime(SettingsTimeField.EarliestWakeUp, LocalTime.of(5, 0))
        viewModel.cancelScheduleEdit()

        assertEquals(original.earliestWakeUp, viewModel.uiState.value.earliestWakeUp)
        assertFalse(viewModel.uiState.value.isEditingSchedule)
    }

    @Test
    fun invalidScheduleValuesAreRejectedWithoutPersistence() = runTest {
        val repository = FakeSettingsRepository()
        val original = repository.settings.value.schedule
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)
        viewModel.beginScheduleEdit()
        scope.advanceUntilIdle()

        listOf(
            GlobalMorningSchedule(LocalTime.of(7, 0), LocalTime.of(7, 0), LocalTime.of(8, 0)),
            GlobalMorningSchedule(LocalTime.of(8, 0), LocalTime.of(7, 0), LocalTime.of(9, 0)),
            GlobalMorningSchedule(LocalTime.of(6, 0), LocalTime.of(8, 0), LocalTime.of(8, 0)),
            GlobalMorningSchedule(LocalTime.of(6, 0), LocalTime.of(9, 0), LocalTime.of(8, 0)),
        ).forEach { invalid ->
            viewModel.updateScheduleTime(SettingsTimeField.EarliestWakeUp, invalid.earliestWakeUp)
            viewModel.updateScheduleTime(SettingsTimeField.LatestWakeUp, invalid.latestWakeUp)
            viewModel.updateScheduleTime(SettingsTimeField.ReadyBy, invalid.readyBy)
            viewModel.saveSchedule()
            scope.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.scheduleErrors.isNotEmpty())
            assertEquals(original, repository.settings.value.schedule)
        }
    }

    @Test
    fun scheduleEditWaitsForReadinessBeforeReadingOrSaving() = runTest {
        val ready = CompletableDeferred<Unit>()
        val original = GlobalMorningSchedule(LocalTime.of(6, 15), LocalTime.of(7, 15), LocalTime.of(8, 15))
        val repository = ControlledSettingsRepository(AppSettings(schedule = original), ready)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)

        viewModel.beginScheduleEdit()
        scope.runCurrent()
        assertFalse(viewModel.uiState.value.isScheduleReady)
        assertFalse(viewModel.uiState.value.isEditingSchedule)
        assertEquals(0, repository.updateCalls)

        ready.complete(Unit)
        scope.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isScheduleReady)

        viewModel.beginScheduleEdit()
        scope.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isEditingSchedule)
        assertEquals(LocalTime.of(6, 15), viewModel.uiState.value.earliestWakeUp)
        assertEquals(original, repository.settings.value.schedule)
    }

    @Test
    fun validScheduleSavePersistsOnlyTheScheduleAndExitsEditMode() = runTest {
        val original = AppSettings(
            schedule = GlobalMorningSchedule(LocalTime.of(6, 0), LocalTime.of(7, 0), LocalTime.of(8, 0)),
            audio = AudioSettings(backgroundMusicVolume = 0.25f),
            reminders = ReminderPreferences(umbrellaRainPercent = 60, jacketTemperatureCelsius = 10, sunscreen = true),
        )
        val repository = ControlledSettingsRepository(original)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)
        scope.advanceUntilIdle()

        viewModel.beginScheduleEdit()
        scope.advanceUntilIdle()
        viewModel.updateScheduleTime(SettingsTimeField.LatestWakeUp, LocalTime.of(7, 30))
        viewModel.saveSchedule()
        scope.advanceUntilIdle()

        assertEquals(LocalTime.of(7, 30), repository.settings.value.schedule.latestWakeUp)
        assertEquals(original.audio, repository.settings.value.audio)
        assertEquals(original.reminders, repository.settings.value.reminders)
        assertFalse(viewModel.uiState.value.isEditingSchedule)
        assertEquals(LocalTime.of(7, 30), viewModel.uiState.value.latestWakeUp)
    }

    @Test
    fun saveFailureKeepsDraftAndShowsError() = runTest {
        val repository = ControlledSettingsRepository(
            AppSettings(schedule = GlobalMorningSchedule(LocalTime.of(6, 0), LocalTime.of(7, 0), LocalTime.of(8, 0))),
            failOnUpdate = true,
        )
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)
        scope.advanceUntilIdle()

        viewModel.beginScheduleEdit()
        scope.advanceUntilIdle()
        viewModel.updateScheduleTime(SettingsTimeField.LatestWakeUp, LocalTime.of(7, 30))
        viewModel.saveSchedule()
        scope.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEditingSchedule)
        assertEquals(LocalTime.of(7, 30), viewModel.uiState.value.latestWakeUp)
        assertEquals(SettingsScheduleError.SaveFailed, viewModel.uiState.value.scheduleError)
    }

    @Test
    fun repositoryUpdatesOtherFieldsDoNotReplaceScheduleDraft() = runTest {
        val repository = FakeSettingsRepository()
        repository.update {
            it.copy(schedule = GlobalMorningSchedule(LocalTime.of(6, 0), LocalTime.of(7, 0), LocalTime.of(8, 0)))
        }
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)
        scope.advanceUntilIdle()

        viewModel.beginScheduleEdit()
        scope.advanceUntilIdle()
        viewModel.updateScheduleTime(SettingsTimeField.LatestWakeUp, LocalTime.of(7, 30))
        repository.update { it.copy(audio = it.audio.copy(backgroundMusicVolume = 0.8f)) }
        scope.advanceUntilIdle()

        assertEquals(LocalTime.of(7, 30), viewModel.uiState.value.latestWakeUp)
        assertEquals(0.8f, viewModel.uiState.value.audioSettings.first().volume)
    }
    @Test
    fun settingsStateReflectsRepositoryValues() = runTest {
        val repository = FakeSettingsRepository()
        repository.update {
            it.copy(
                schedule = GlobalMorningSchedule(
                    earliestWakeUp = LocalTime.of(6, 10),
                    latestWakeUp = LocalTime.of(6, 40),
                    readyBy = LocalTime.of(8, 15),
                ),
                audio = AudioSettings(
                    backgroundMusicEnabled = false,
                    backgroundMusicVolume = 0.55f,
                    voiceGuidanceVolume = 0.35f,
                    audioCuesEnabled = false,
                ),
            )
        }
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)
        scope.advanceUntilIdle()

        assertEquals(LocalTime.of(6, 10), viewModel.uiState.value.earliestWakeUp)
        assertEquals(LocalTime.of(6, 40), viewModel.uiState.value.latestWakeUp)
        assertEquals(0.55f, viewModel.uiState.value.audioSettings.first().volume)
        assertFalse(viewModel.uiState.value.audioSettings.first().enabled)
        assertFalse(viewModel.uiState.value.audioSettings.last().enabled)
    }

    @Test
    fun supportedAudioAndThingsChangesAreSavedWithoutOverwritingOtherSettings() = runTest {
        val repository = FakeSettingsRepository()
        repository.update {
            it.copy(
                schedule = GlobalMorningSchedule(
                    earliestWakeUp = LocalTime.of(6, 10),
                    latestWakeUp = LocalTime.of(6, 40),
                    readyBy = LocalTime.of(8, 15),
                ),
                reminders = ReminderPreferences(
                    umbrellaRainPercent = null,
                    jacketTemperatureCelsius = 8,
                    sunscreen = false,
                ),
            )
        }
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)

        viewModel.toggle(SettingsToggleKey.BackgroundMusic)
        viewModel.setVolume(SettingsToggleKey.BackgroundMusic, 0.6f)
        viewModel.removeThingToBring("Keys")
        viewModel.addThingToBring("  Umbrella ")
        viewModel.addThingToBring("umbrella")
        scope.advanceUntilIdle()

        val settings = repository.settings.value
        assertEquals(LocalTime.of(6, 10), settings.schedule.earliestWakeUp)
        assertEquals(LocalTime.of(6, 40), settings.schedule.latestWakeUp)
        assertEquals(LocalTime.of(8, 15), settings.schedule.readyBy)
        assertEquals(false, settings.audio.backgroundMusicEnabled)
        assertEquals(0.6f, settings.audio.backgroundMusicVolume)
        assertEquals(
            ReminderPreferences(
                umbrellaRainPercent = null,
                jacketTemperatureCelsius = 8,
                sunscreen = false,
            ),
            settings.reminders,
        )
        assertFalse(settings.thingsToBring.any { it.equals("Keys", ignoreCase = true) })
        assertEquals(1, settings.thingsToBring.count { it.equals("Umbrella", ignoreCase = true) })
        assertEquals("Umbrella", settings.thingsToBring.last())
    }

    @Test
    fun voiceGuidanceToggleAndVolumeAreSavedIndependently() = runTest {
        val repository = FakeSettingsRepository()
        repository.update {
            it.copy(
                audio = it.audio.copy(
                    backgroundMusicVolume = 0.2f,
                    voiceGuidanceEnabled = false,
                    voiceGuidanceVolume = 0.35f,
                ),
            )
        }
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)

        viewModel.toggle(SettingsToggleKey.VoiceGuidance)
        viewModel.setVolume(SettingsToggleKey.VoiceGuidance, 0.7f)
        scope.advanceUntilIdle()

        assertTrue(repository.settings.value.audio.voiceGuidanceEnabled)
        assertEquals(0.7f, repository.settings.value.audio.voiceGuidanceVolume)
        assertEquals(0.2f, repository.settings.value.audio.backgroundMusicVolume)
    }

    @Test
    fun reminderOptionsAndSunscreenAreSavedUsingFormalValues() = runTest {
        val repository = FakeSettingsRepository()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)

        assertEquals(40, repository.settings.value.reminders.umbrellaRainPercent)
        assertEquals(8, repository.settings.value.reminders.jacketTemperatureCelsius)
        assertFalse(repository.settings.value.reminders.sunscreen)

        listOf(60, 80, null, 20, 40).forEach { expected ->
            viewModel.selectReminder(SettingsPreferenceKey.Umbrella, expected)
            scope.advanceUntilIdle()
            assertEquals(expected, repository.settings.value.reminders.umbrellaRainPercent)
        }
        listOf(10, 15, null, 5, 8).forEach { expected ->
            viewModel.selectReminder(SettingsPreferenceKey.Jacket, expected)
            scope.advanceUntilIdle()
            assertEquals(expected, repository.settings.value.reminders.jacketTemperatureCelsius)
        }

        viewModel.selectReminder(SettingsPreferenceKey.Sunscreen)
        scope.advanceUntilIdle()
        assertTrue(repository.settings.value.reminders.sunscreen)
        viewModel.selectReminder(SettingsPreferenceKey.Sunscreen)
        scope.advanceUntilIdle()
        assertFalse(repository.settings.value.reminders.sunscreen)
    }

    @Test
    fun permissionStateIsReadIntoDisplayDataWithoutPersistence() = runTest {
        val repository = FakeSettingsRepository()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)

        viewModel.refreshPermissionState(
            locationGranted = true,
            calendarGranted = false,
            notificationAvailability = NotificationAvailability.RUNTIME_PERMISSION_DENIED,
            exactAlarmGranted = false,
        )
        scope.advanceUntilIdle()

        val permissions = viewModel.uiState.value.permissions
        assertEquals(SettingsPermissionStatus.Granted, permissions[0].status)
        assertEquals(SettingsPermissionStatus.Denied, permissions[1].status)
        assertEquals(SettingsPermissionStatus.NotAvailable, permissions[2].status)
        assertEquals(SettingsPermissionStatus.Denied, permissions[3].status)
        assertEquals(SettingsPermissionStatus.Denied, permissions[4].status)
        assertEquals(SettingsPermissionKey.AlarmsAndReminders, permissions[4].key)
        assertEquals(AppSettings(), repository.settings.value)
    }

    @Test
    fun notificationAvailabilityMapsAllStatesAndRefreshesWithoutPersistence() = runTest {
        val repository = FakeSettingsRepository()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)

        NotificationAvailability.entries.forEach { availability ->
            viewModel.refreshPermissionState(
                locationGranted = true,
                calendarGranted = true,
                notificationAvailability = availability,
            )
            scope.runCurrent()
            val expected = if (availability == NotificationAvailability.AVAILABLE) {
                SettingsPermissionStatus.Granted
            } else {
                SettingsPermissionStatus.Denied
            }
            assertEquals(
                expected,
                viewModel.uiState.value.permissions.single {
                    it.key == SettingsPermissionKey.Notifications
                }.status,
            )
        }

        assertEquals(AppSettings(), repository.settings.value)
    }

    @Test
    fun exactAlarmPermissionRefreshesInBothDirectionsWithoutPersistence() = runTest {
        val repository = FakeSettingsRepository()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)

        viewModel.refreshPermissionState(
            locationGranted = true,
            calendarGranted = true,
            sensorsGranted = true,
            exactAlarmGranted = false,
        )
        scope.advanceUntilIdle()
        assertEquals(
            SettingsPermissionStatus.Denied,
            viewModel.uiState.value.permissions.single {
                it.key == SettingsPermissionKey.AlarmsAndReminders
            }.status,
        )

        viewModel.refreshPermissionState(
            locationGranted = true,
            calendarGranted = true,
            sensorsGranted = true,
            exactAlarmGranted = true,
        )
        scope.advanceUntilIdle()
        assertEquals(
            SettingsPermissionStatus.Granted,
            viewModel.uiState.value.permissions.single {
                it.key == SettingsPermissionKey.AlarmsAndReminders
            }.status,
        )

        viewModel.refreshPermissionState(
            locationGranted = true,
            calendarGranted = true,
            sensorsGranted = true,
            exactAlarmGranted = false,
        )
        scope.advanceUntilIdle()
        assertEquals(
            SettingsPermissionStatus.Denied,
            viewModel.uiState.value.permissions.single {
                it.key == SettingsPermissionKey.AlarmsAndReminders
            }.status,
        )
        assertEquals(AppSettings(), repository.settings.value)
    }

    @Test
    fun sensorPermissionStateIsReadWithoutPersistence() = runTest {
        val repository = FakeSettingsRepository()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)

        viewModel.refreshPermissionState(locationGranted = true, calendarGranted = true, sensorsGranted = false)
        scope.advanceUntilIdle()
        assertEquals(SettingsPermissionStatus.Denied, viewModel.uiState.value.permissions[2].status)

        viewModel.refreshPermissionState(locationGranted = true, calendarGranted = true, sensorsGranted = true)
        scope.advanceUntilIdle()
        assertEquals(SettingsPermissionStatus.Granted, viewModel.uiState.value.permissions[2].status)
        assertEquals(AppSettings(), repository.settings.value)
    }

    @Test
    fun schedulerStatusShowsOnlySupportedFailureWarningsAndUpdates() = runTest {
        val repository = FakeSettingsRepository()
        val status = MutableStateFlow<WakeupScheduleStatus>(WakeupScheduleStatus.NotScheduled)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope, status)
        scope.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.schedulerWarning)

        status.value = WakeupScheduleStatus.Scheduled
        scope.runCurrent()
        assertEquals(null, viewModel.uiState.value.schedulerWarning)

        status.value = WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.EXACT_ACCESS_REQUIRED)
        scope.runCurrent()
        assertEquals(
            SettingsSchedulerWarning.ExactAccessRequired,
            viewModel.uiState.value.schedulerWarning,
        )
        assertTrue(viewModel.uiState.value.schedulerWarning?.hasOpenSettingsAction == true)

        status.value = WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.PLATFORM_REJECTED)
        scope.runCurrent()
        assertEquals(
            SettingsSchedulerWarning.PlatformRejected,
            viewModel.uiState.value.schedulerWarning,
        )
        assertFalse(viewModel.uiState.value.schedulerWarning?.hasOpenSettingsAction == true)

        status.value = WakeupScheduleStatus.Scheduled
        scope.runCurrent()
        assertEquals(null, viewModel.uiState.value.schedulerWarning)
    }

    private class ControlledSettingsRepository(
        initial: AppSettings,
        private val readyGate: CompletableDeferred<Unit>? = null,
        private val failOnUpdate: Boolean = false,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = state
        var updateCalls = 0
            private set

        override suspend fun awaitReady() {
            readyGate?.await()
        }

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            updateCalls++
            if (failOnUpdate) error("test settings failure")
            state.value = transform(state.value)
        }
    }

    @Test
    fun validationErrorKeepsDraftEditableUntilItIsCorrected() = runTest {
        val repository = FakeSettingsRepository()
        val original = repository.settings.value.schedule
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)
        scope.advanceUntilIdle()

        viewModel.beginScheduleEdit()
        scope.advanceUntilIdle()
        viewModel.updateScheduleTime(SettingsTimeField.LatestWakeUp, LocalTime.of(6, 0))
        viewModel.saveSchedule()

        assertTrue(viewModel.uiState.value.scheduleErrors.isNotEmpty())
        assertTrue(viewModel.uiState.value.isEditingSchedule)
        assertEquals(LocalTime.of(6, 0), viewModel.uiState.value.latestWakeUp)

        viewModel.updateScheduleTime(SettingsTimeField.LatestWakeUp, original.latestWakeUp)
        assertTrue(viewModel.uiState.value.scheduleErrors.isEmpty())
        viewModel.saveSchedule()
        scope.advanceUntilIdle()

        assertEquals(original, repository.settings.value.schedule)
        assertFalse(viewModel.uiState.value.isEditingSchedule)
    }

    @Test
    fun beginScheduleEditReusesExistingDraftForPickerReentry() = runTest {
        val repository = FakeSettingsRepository()
        val original = repository.settings.value.schedule
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val viewModel = SettingsViewModel(repository, scope)
        scope.advanceUntilIdle()

        viewModel.beginScheduleEdit()
        scope.advanceUntilIdle()
        val editedEarliest = LocalTime.of(5, 30)
        viewModel.updateScheduleTime(SettingsTimeField.EarliestWakeUp, editedEarliest)

        var callbackSchedule: GlobalMorningSchedule? = null
        viewModel.beginScheduleEdit { callbackSchedule = it }

        assertEquals(
            GlobalMorningSchedule(editedEarliest, original.latestWakeUp, original.readyBy),
            callbackSchedule,
        )
        assertEquals(original, repository.settings.value.schedule)
    }
}

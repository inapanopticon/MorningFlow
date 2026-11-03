package com.morningflow.app.feature.onboarding

import com.morningflow.app.core.data.FakeSettingsRepository
import com.morningflow.app.core.data.FakeTemplateRepository
import com.morningflow.app.core.data.SettingsRepository
import com.morningflow.app.core.data.TemplateRepository
import com.morningflow.app.core.data.notification.api.NotificationAvailability
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.OnboardingStep
import com.morningflow.app.core.model.RECOMMENDED_TEMPLATE_ID
import com.morningflow.app.core.model.RoutineTemplate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

@kotlinx.coroutines.ExperimentalCoroutinesApi
class OnboardingViewModelTest {
    @Test
    fun beginPersistsWakeUpSetupStep() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = OnboardingViewModel(settings, FakeTemplateRepository(), CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        viewModel.begin()
        advanceUntilIdle()

        assertEquals(OnboardingStep.WAKE_UP_SETUP, settings.settings.value.onboardingStep)
        assertEquals(OnboardingStep.WAKE_UP_SETUP, viewModel.uiState.value.step)
    }

    @Test
    fun beginCannotBeSubmittedTwiceWhilePersisting() = runTest {
        val settings = RecordingSettingsRepository()
        val viewModel = OnboardingViewModel(
            settings,
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()

        viewModel.begin()
        viewModel.begin()
        advanceUntilIdle()

        assertEquals(1, settings.updateCount)
        assertFalse(viewModel.uiState.value.isBeginning)
        assertEquals(OnboardingStep.WAKE_UP_SETUP, viewModel.uiState.value.step)
    }

    @Test
    fun firstSetupStateHasNoDraftTimesOrDays() = runTest {
        val viewModel = OnboardingViewModel(
            FakeSettingsRepository(),
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )

        assertEquals(null, viewModel.uiState.value.earliestWakeUp)
        assertEquals(null, viewModel.uiState.value.latestWakeUp)
        assertEquals(null, viewModel.uiState.value.readyBy)
        assertTrue(viewModel.uiState.value.activeDays.isEmpty())
    }

    @Test
    fun permissionStateStartsUnavailableUntilPlatformStateIsRead() = runTest {
        val viewModel = OnboardingViewModel(
            FakeSettingsRepository(),
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )

        assertEquals(PermissionAccess.NotAvailable, viewModel.uiState.value.locationPermission)
        assertEquals(PermissionAccess.NotAvailable, viewModel.uiState.value.calendarPermission)
        assertEquals(PermissionAccess.NotAvailable, viewModel.uiState.value.sensorsPermission)
        assertEquals(PermissionAccess.NotAvailable, viewModel.uiState.value.notificationPermission)
        assertEquals(PermissionAccess.NotAvailable, viewModel.uiState.value.exactAlarmPermission)
    }

    @Test
    fun permissionStateReflectsGrantedAndDeniedPlatformResults() = runTest {
        val viewModel = OnboardingViewModel(
            FakeSettingsRepository(),
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )

        viewModel.refreshPermissionState(
            locationGranted = true,
            calendarGranted = false,
            sensorsGranted = true,
            notificationAvailability = NotificationAvailability.RUNTIME_PERMISSION_DENIED,
            exactAlarmGranted = false,
        )

        assertEquals(PermissionAccess.Granted, viewModel.uiState.value.locationPermission)
        assertEquals(PermissionAccess.Denied, viewModel.uiState.value.calendarPermission)
        assertEquals(PermissionAccess.Granted, viewModel.uiState.value.sensorsPermission)
        assertEquals(PermissionAccess.Denied, viewModel.uiState.value.notificationPermission)
        assertEquals(PermissionAccess.Denied, viewModel.uiState.value.exactAlarmPermission)
    }

    @Test
    fun notificationAvailabilityMapsAllPlatformStates() = runTest {
        val viewModel = OnboardingViewModel(
            FakeSettingsRepository(),
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )

        NotificationAvailability.entries.forEach { availability ->
            viewModel.refreshPermissionState(
                locationGranted = true,
                calendarGranted = true,
                notificationAvailability = availability,
            )
            val expected = if (availability == NotificationAvailability.AVAILABLE) {
                PermissionAccess.Granted
            } else {
                PermissionAccess.Denied
            }
            assertEquals(expected, viewModel.uiState.value.notificationPermission)
        }
    }

    @Test
    fun permissionStateCanRefreshAfterReturningFromSystemSettings() = runTest {
        val viewModel = OnboardingViewModel(
            FakeSettingsRepository(),
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )

        viewModel.refreshPermissionState(
            locationGranted = false,
            calendarGranted = false,
            sensorsGranted = false,
            notificationAvailability = NotificationAvailability.CHANNEL_DISABLED,
            exactAlarmGranted = false,
        )
        viewModel.refreshPermissionState(
            locationGranted = true,
            calendarGranted = true,
            sensorsGranted = true,
            notificationAvailability = NotificationAvailability.AVAILABLE,
            exactAlarmGranted = true,
        )

        assertEquals(PermissionAccess.Granted, viewModel.uiState.value.locationPermission)
        assertEquals(PermissionAccess.Granted, viewModel.uiState.value.calendarPermission)
        assertEquals(PermissionAccess.Granted, viewModel.uiState.value.sensorsPermission)
        assertEquals(PermissionAccess.Granted, viewModel.uiState.value.notificationPermission)
        assertEquals(PermissionAccess.Granted, viewModel.uiState.value.exactAlarmPermission)
    }

    @Test
    fun exactAlarmRowRequestsSpecialAccessOnlyWhenDenied() {
        var requests = 0
        val onRequest: () -> Unit = { requests++ }

        exactAlarmRequestAction(PermissionAccess.Denied, onRequest)?.invoke()

        assertEquals(1, requests)
        assertEquals(null, exactAlarmRequestAction(PermissionAccess.Granted, onRequest))
        assertEquals(null, exactAlarmRequestAction(PermissionAccess.NotAvailable, onRequest))
    }

    @Test
    fun draftInputAndDaySelectionStayInUiState() = runTest {
        val viewModel = OnboardingViewModel(
            FakeSettingsRepository(),
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        val earliest = LocalTime.of(6, 30)
        val latest = LocalTime.of(7, 0)
        val readyBy = LocalTime.of(8, 30)

        viewModel.setEarliestWakeUp(earliest)
        viewModel.setLatestWakeUp(latest)
        viewModel.setReadyBy(readyBy)
        viewModel.toggleActiveDay(DayOfWeek.MONDAY)

        assertEquals(earliest, viewModel.uiState.value.earliestWakeUp)
        assertEquals(latest, viewModel.uiState.value.latestWakeUp)
        assertEquals(readyBy, viewModel.uiState.value.readyBy)
        assertEquals(setOf(DayOfWeek.MONDAY), viewModel.uiState.value.activeDays)
    }

    @Test
    fun recreatedViewModelResumesFromPersistedStepWithoutRestoringDraft() = runTest {
        OnboardingStep.entries.forEach { persistedStep ->
            val settings = FakeSettingsRepository()
            settings.update { it.copy(onboardingStep = persistedStep) }
            val viewModel = OnboardingViewModel(
                settings,
                FakeTemplateRepository(),
                CoroutineScope(StandardTestDispatcher(testScheduler)),
            )
            advanceUntilIdle()

            assertEquals(persistedStep, viewModel.uiState.value.step)
            assertEquals(null, viewModel.uiState.value.earliestWakeUp)
            assertEquals(null, viewModel.uiState.value.latestWakeUp)
            assertEquals(null, viewModel.uiState.value.readyBy)
            assertTrue(viewModel.uiState.value.activeDays.isEmpty())
        }
    }

    @Test
    fun invalidSubmitKeepsDraftAndDoesNotWriteOrAdvance() = runTest {
        val settings = RecordingSettingsRepository()
        val templates = RecordingTemplateRepository()
        val viewModel = OnboardingViewModel(settings, templates, CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        viewModel.begin()
        advanceUntilIdle()

        viewModel.setEarliestWakeUp(LocalTime.of(8, 0))
        viewModel.setLatestWakeUp(LocalTime.of(7, 0))
        viewModel.setReadyBy(LocalTime.of(9, 0))
        viewModel.next()
        advanceUntilIdle()

        assertEquals(OnboardingStep.WAKE_UP_SETUP, settings.settings.value.onboardingStep)
        assertEquals(1, settings.updateCount)
        assertEquals(0, templates.saveCount)
        assertTrue(OnboardingField.LATEST_WAKE_UP in viewModel.uiState.value.errors)
        assertEquals(DayOfWeek.entries.take(5).toSet(), viewModel.uiState.value.activeDays)
        assertEquals(LocalTime.of(8, 0), viewModel.uiState.value.earliestWakeUp)
    }

    @Test
    fun beginningSuggestsAWeekdayScheduleInsteadOfBlankFields() = runTest {
        val viewModel = OnboardingViewModel(
            RecordingSettingsRepository(),
            RecordingTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()
        viewModel.begin()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalTime.of(7, 0), state.earliestWakeUp)
        assertEquals(LocalTime.of(7, 30), state.latestWakeUp)
        assertEquals(LocalTime.of(8, 30), state.readyBy)
        assertEquals(DayOfWeek.entries.take(5).toSet(), state.activeDays)
    }

    @Test
    fun requiredStrictOrderingAndRemovingLastDayAreValidatedIndependently() = runTest {
        val settings = FakeSettingsRepository()
        settings.update { it.copy(onboardingStep = OnboardingStep.WAKE_UP_SETUP) }
        val viewModel = OnboardingViewModel(
            settings,
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()

        viewModel.next()
        assertEquals(
            setOf(
                OnboardingField.EARLIEST_WAKE_UP,
                OnboardingField.LATEST_WAKE_UP,
                OnboardingField.READY_BY,
                OnboardingField.ACTIVE_DAYS,
            ),
            viewModel.uiState.value.errors,
        )

        viewModel.setEarliestWakeUp(LocalTime.of(7, 0))
        viewModel.setLatestWakeUp(LocalTime.of(7, 0))
        viewModel.setReadyBy(LocalTime.of(7, 0))
        viewModel.toggleActiveDay(DayOfWeek.MONDAY)
        viewModel.next()
        assertEquals(
            setOf(OnboardingField.LATEST_WAKE_UP, OnboardingField.READY_BY),
            viewModel.uiState.value.errors,
        )

        viewModel.setLatestWakeUp(LocalTime.of(8, 0))
        viewModel.setReadyBy(LocalTime.of(9, 0))
        viewModel.toggleActiveDay(DayOfWeek.MONDAY)
        viewModel.next()
        assertEquals(setOf(OnboardingField.ACTIVE_DAYS), viewModel.uiState.value.errors)
    }

    @Test
    fun validSubmitUpdatesExistingRecommendedMorningAndAdvances() = runTest {
        val settings = RecordingSettingsRepository()
        val templates = RecordingTemplateRepository()
        val original = templates.templates.value.single { it.id == RECOMMENDED_TEMPLATE_ID }
        val viewModel = OnboardingViewModel(settings, templates, CoroutineScope(StandardTestDispatcher(testScheduler)))
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY)
        advanceUntilIdle()
        viewModel.begin()
        advanceUntilIdle()

        viewModel.setEarliestWakeUp(LocalTime.of(6, 15))
        viewModel.setLatestWakeUp(LocalTime.of(7, 0))
        viewModel.setReadyBy(LocalTime.of(8, 30))
        // Clear the suggested weekdays, then pick the days under test.
        viewModel.uiState.value.activeDays.forEach(viewModel::toggleActiveDay)
        days.forEach(viewModel::toggleActiveDay)
        viewModel.next()
        advanceUntilIdle()

        val saved = templates.templates.value.single { it.id == RECOMMENDED_TEMPLATE_ID }
        assertEquals(1, templates.saveCount)
        assertEquals(1, templates.setDefaultCount)
        assertEquals(3, settings.updateCount)
        assertEquals(LocalTime.of(6, 15), settings.settings.value.schedule.earliestWakeUp)
        assertEquals(LocalTime.of(7, 0), settings.settings.value.schedule.latestWakeUp)
        assertEquals(LocalTime.of(8, 30), settings.settings.value.schedule.readyBy)
        assertEquals(days, saved.applicableDays)
        assertEquals(LocalTime.of(6, 15), saved.applicableStart)
        assertEquals(original.applicableEnd, saved.applicableEnd)
        assertEquals(5, saved.priority)
        assertEquals(original.modules, saved.modules)
        assertTrue(saved.isDefault)
        assertEquals(OnboardingStep.PERMISSIONS, settings.settings.value.onboardingStep)
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.uiState.value.step)
    }

    @Test
    fun validSubmitWaitsForTemplateRepositoryReadinessBeforeReadingRecommendedMorning() = runTest {
        val settings = RecordingSettingsRepository()
        val ready = CompletableDeferred<Unit>()
        val templates = RecordingTemplateRepository(readyGate = ready)
        val viewModel = OnboardingViewModel(
            settings,
            templates,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()
        viewModel.begin()
        advanceUntilIdle()
        viewModel.setEarliestWakeUp(LocalTime.of(6, 15))
        viewModel.setLatestWakeUp(LocalTime.of(7, 0))
        viewModel.setReadyBy(LocalTime.of(8, 30))
        viewModel.toggleActiveDay(DayOfWeek.MONDAY)

        viewModel.next()
        runCurrent()

        assertEquals(0, templates.saveCount)
        assertEquals(OnboardingStep.WAKE_UP_SETUP, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.isSaving)

        ready.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, templates.saveCount)
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.uiState.value.step)
    }

    @Test
    fun saveFailureLeavesSetupRetryableAndDoesNotAdvance() = runTest {
        val settings = RecordingSettingsRepository()
        val templates = RecordingTemplateRepository(failOnSave = true)
        val viewModel = OnboardingViewModel(settings, templates, CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        viewModel.begin()
        advanceUntilIdle()
        viewModel.setEarliestWakeUp(LocalTime.of(6, 0))
        viewModel.setLatestWakeUp(LocalTime.of(7, 0))
        viewModel.setReadyBy(LocalTime.of(8, 0))
        viewModel.toggleActiveDay(DayOfWeek.MONDAY)

        viewModel.next()
        advanceUntilIdle()

        assertEquals(OnboardingStep.WAKE_UP_SETUP, settings.settings.value.onboardingStep)
        assertEquals(OnboardingStep.WAKE_UP_SETUP, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.saveFailed)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(LocalTime.of(6, 0), viewModel.uiState.value.earliestWakeUp)
    }

    @Test
    fun saveFailureCanBeRetriedSuccessfully() = runTest {
        val settings = RecordingSettingsRepository()
        val templates = RecordingTemplateRepository(failOnSave = true)
        val viewModel = OnboardingViewModel(
            settings,
            templates,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()
        viewModel.begin()
        advanceUntilIdle()
        viewModel.setEarliestWakeUp(LocalTime.of(6, 0))
        viewModel.setLatestWakeUp(LocalTime.of(7, 0))
        viewModel.setReadyBy(LocalTime.of(8, 0))
        viewModel.toggleActiveDay(DayOfWeek.MONDAY)

        viewModel.next()
        advanceUntilIdle()
        templates.failOnSave = false
        viewModel.next()
        advanceUntilIdle()

        assertEquals(OnboardingStep.PERMISSIONS, settings.settings.value.onboardingStep)
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.saveFailed)
        assertEquals(2, templates.saveCount)
    }

    @Test
    fun completingPermissionsPersistsCompleteStep() = runTest {
        val settings = FakeSettingsRepository()
        settings.update { it.copy(onboardingStep = OnboardingStep.PERMISSIONS) }
        val viewModel = OnboardingViewModel(
            settings,
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()

        viewModel.completePermissions()
        advanceUntilIdle()

        assertEquals(OnboardingStep.COMPLETE, settings.settings.value.onboardingStep)
        assertEquals(OnboardingStep.COMPLETE, viewModel.uiState.value.step)
    }

    @Test
    fun settingsReadinessHidesDefaultWelcomeAndDoesNotOverwriteCompletedOnboarding() = runTest {
        val ready = CompletableDeferred<Unit>()
        val settings = RecordingSettingsRepository(readyGate = ready)
        settings.delegateUpdate { it.copy(onboardingStep = OnboardingStep.COMPLETE) }
        val viewModel = OnboardingViewModel(
            settings,
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isReady)
        viewModel.begin()
        assertEquals(0, settings.updateCount)

        ready.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isReady)
        assertEquals(OnboardingStep.COMPLETE, viewModel.uiState.value.step)
        assertEquals(OnboardingStep.COMPLETE, settings.settings.value.onboardingStep)
        assertEquals(0, settings.updateCount)
    }

    @Test
    fun realWelcomeUserCanBeginAfterSettingsReadiness() = runTest {
        val ready = CompletableDeferred<Unit>()
        val settings = RecordingSettingsRepository(readyGate = ready)
        val viewModel = OnboardingViewModel(
            settings,
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        runCurrent()
        ready.complete(Unit)
        advanceUntilIdle()

        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.step)
        viewModel.begin()
        advanceUntilIdle()

        assertEquals(OnboardingStep.WAKE_UP_SETUP, settings.settings.value.onboardingStep)
    }

    @Test
    fun readinessGuardsOtherStepChangingActions() = runTest {
        val ready = CompletableDeferred<Unit>()
        val settings = RecordingSettingsRepository(readyGate = ready)
        settings.delegateUpdate { it.copy(onboardingStep = OnboardingStep.PERMISSIONS) }
        val viewModel = OnboardingViewModel(
            settings,
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        runCurrent()

        viewModel.next()
        viewModel.completePermissions()
        assertEquals(0, settings.updateCount)

        ready.complete(Unit)
        advanceUntilIdle()

        assertEquals(OnboardingStep.PERMISSIONS, settings.settings.value.onboardingStep)
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.uiState.value.step)
        assertEquals(0, settings.updateCount)
    }

    @Test
    fun permissionsNextCallbackAdvancesFromPermissionsToComplete() = runTest {
        val settings = FakeSettingsRepository()
        settings.update { it.copy(onboardingStep = OnboardingStep.PERMISSIONS) }
        val viewModel = OnboardingViewModel(
            settings,
            FakeTemplateRepository(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()
        viewModel.refreshPermissionState(
            locationGranted = false,
            calendarGranted = false,
            sensorsGranted = false,
            notificationAvailability = NotificationAvailability.RUNTIME_PERMISSION_DENIED,
            exactAlarmGranted = false,
        )

        dispatchPermissionsNext(viewModel::completePermissions)
        advanceUntilIdle()

        assertEquals(OnboardingStep.COMPLETE, viewModel.uiState.value.step)
        assertEquals(OnboardingStep.COMPLETE, settings.settings.value.onboardingStep)
    }

    @Test
    fun draftChangesAreIgnoredWhileSaving() = runTest {
        val settings = FakeSettingsRepository()
        settings.update { it.copy(onboardingStep = OnboardingStep.WAKE_UP_SETUP) }
        val saveGate = CompletableDeferred<Unit>()
        val templates = RecordingTemplateRepository(saveGate = saveGate)
        val viewModel = OnboardingViewModel(
            settings,
            templates,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()
        viewModel.setEarliestWakeUp(LocalTime.of(6, 0))
        viewModel.setLatestWakeUp(LocalTime.of(7, 0))
        viewModel.setReadyBy(LocalTime.of(8, 0))
        viewModel.toggleActiveDay(DayOfWeek.MONDAY)
        viewModel.next()
        advanceUntilIdle()

        viewModel.setEarliestWakeUp(LocalTime.of(5, 0))
        viewModel.toggleActiveDay(DayOfWeek.TUESDAY)

        assertTrue(viewModel.uiState.value.isSaving)
        assertEquals(LocalTime.of(6, 0), viewModel.uiState.value.earliestWakeUp)
        assertEquals(setOf(DayOfWeek.MONDAY), viewModel.uiState.value.activeDays)

        saveGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.uiState.value.step)
    }

    @Test
    fun scheduleTemplateAndDefaultFailuresDoNotAdvanceOnboarding() = runTest {
        val scheduleSettings = RecordingSettingsRepository()
        val scheduleTemplates = RecordingTemplateRepository()
        val scheduleViewModel = readyViewModel(scheduleSettings, scheduleTemplates, testScheduler)
        scheduleSettings.failOnSchedule = true
        scheduleViewModel.next()
        advanceUntilIdle()
        assertEquals(OnboardingStep.WAKE_UP_SETUP, scheduleSettings.settings.value.onboardingStep)
        assertEquals(0, scheduleTemplates.saveCount)

        val templateSettings = RecordingSettingsRepository()
        val templateTemplates = RecordingTemplateRepository(failOnSave = true)
        val templateViewModel = readyViewModel(templateSettings, templateTemplates, testScheduler)
        templateViewModel.next()
        advanceUntilIdle()
        assertEquals(OnboardingStep.WAKE_UP_SETUP, templateSettings.settings.value.onboardingStep)
        assertEquals(1, templateSettings.scheduleUpdateCount)
        assertEquals(0, templateTemplates.setDefaultCount)

        val defaultSettings = RecordingSettingsRepository()
        val defaultTemplates = RecordingTemplateRepository(failOnSetDefault = true)
        val defaultViewModel = readyViewModel(defaultSettings, defaultTemplates, testScheduler)
        defaultViewModel.next()
        advanceUntilIdle()
        assertEquals(OnboardingStep.WAKE_UP_SETUP, defaultSettings.settings.value.onboardingStep)
        assertEquals(1, defaultTemplates.saveCount)
        assertEquals(1, defaultTemplates.setDefaultCount)
    }

    private suspend fun readyViewModel(
        settings: RecordingSettingsRepository,
        templates: RecordingTemplateRepository,
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
    ): OnboardingViewModel {
        settings.delegateUpdate { it.copy(onboardingStep = OnboardingStep.WAKE_UP_SETUP) }
        return OnboardingViewModel(
            settings,
            templates,
            CoroutineScope(StandardTestDispatcher(scheduler)),
        ).also { viewModel ->
            scheduler.runCurrent()
            viewModel.setEarliestWakeUp(LocalTime.of(6, 0))
            viewModel.setLatestWakeUp(LocalTime.of(7, 0))
            viewModel.setReadyBy(LocalTime.of(8, 0))
            viewModel.toggleActiveDay(DayOfWeek.MONDAY)
        }
    }

    private class RecordingSettingsRepository(
        private val readyGate: CompletableDeferred<Unit>? = null,
    ) : SettingsRepository {
        private val delegate = FakeSettingsRepository()
        override val settings: StateFlow<AppSettings> = delegate.settings
        var updateCount = 0
        var scheduleUpdateCount = 0
        var failOnSchedule = false

        override suspend fun awaitReady() {
            readyGate?.await()
            delegate.awaitReady()
        }

        suspend fun delegateUpdate(transform: (AppSettings) -> AppSettings) {
            delegate.update(transform)
        }

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            updateCount++
            val before = delegate.settings.value
            val after = transform(before)
            if (after.schedule != before.schedule) {
                scheduleUpdateCount++
                if (failOnSchedule) error("test schedule failure")
            }
            delegate.update { after }
        }
    }

    private class RecordingTemplateRepository(
        var failOnSave: Boolean = false,
        private val saveGate: CompletableDeferred<Unit>? = null,
        private val failOnSetDefault: Boolean = false,
        private val readyGate: CompletableDeferred<Unit>? = null,
    ) : TemplateRepository {
        private val delegate = FakeTemplateRepository()
        override val templates: StateFlow<List<RoutineTemplate>> = delegate.templates
        var saveCount = 0
        var setDefaultCount = 0

        override suspend fun awaitReady() {
            readyGate?.await() ?: delegate.awaitReady()
        }

        override suspend fun save(template: RoutineTemplate) {
            saveCount++
            if (failOnSave) error("test save failure")
            saveGate?.await()
            delegate.save(template)
        }

        override suspend fun delete(templateId: String) = delegate.delete(templateId)

        override suspend fun setDefault(templateId: String) {
            setDefaultCount++
            if (failOnSetDefault) error("test default failure")
            delegate.setDefault(templateId)
        }
    }
}

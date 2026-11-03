package com.morningflow.app.core.wakeup

import com.morningflow.app.core.data.SettingsRepository
import com.morningflow.app.core.data.WakeupScheduleStatus
import com.morningflow.app.core.data.WakeupScheduler
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.GlobalMorningSchedule
import com.morningflow.app.core.model.OnboardingStep
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WakeupScheduleCoordinatorTest {
    private val firstSchedule = GlobalMorningSchedule(
        earliestWakeUp = LocalTime.of(7, 0),
        latestWakeUp = LocalTime.of(7, 30),
    )

    @Test
    fun waitsForSettingsReadinessBeforeReconciling() = runTest {
        val repository = ControllableSettingsRepository(
            AppSettings(onboardingStep = OnboardingStep.COMPLETE, schedule = firstSchedule),
        )
        val scheduler = RecordingWakeupScheduler()
        WakeupScheduleCoordinator(repository, scheduler).start(backgroundScope)

        runCurrent()
        assertTrue(scheduler.calls.isEmpty())

        repository.completeFirstLoad()
        runCurrent()

        assertEquals(listOf(SchedulerCall.Schedule(firstSchedule)), scheduler.calls)
    }

    @Test
    fun incompleteOnboardingCancelsAnyPreviouslyRegisteredAlarm() = runTest {
        val repository = ControllableSettingsRepository(AppSettings(), initiallyReady = true)
        val scheduler = RecordingWakeupScheduler()

        WakeupScheduleCoordinator(repository, scheduler).start(backgroundScope)
        runCurrent()

        assertEquals(listOf(SchedulerCall.Cancel), scheduler.calls)
        assertEquals(WakeupScheduleStatus.NotScheduled, scheduler.status.value)
    }

    @Test
    fun onlyOnboardingOrScheduleChangesTriggerAnotherReconcile() = runTest {
        val repository = ControllableSettingsRepository(
            AppSettings(onboardingStep = OnboardingStep.COMPLETE, schedule = firstSchedule),
            initiallyReady = true,
        )
        val scheduler = RecordingWakeupScheduler()
        WakeupScheduleCoordinator(repository, scheduler).start(backgroundScope)
        runCurrent()

        repository.update { it.copy(thingsToBring = listOf("Keys")) }
        runCurrent()
        assertEquals(1, scheduler.calls.size)

        val later = firstSchedule.copy(
            earliestWakeUp = LocalTime.of(8, 0),
            latestWakeUp = LocalTime.of(8, 30),
        )
        repository.update { it.copy(schedule = later) }
        runCurrent()

        repository.update { it.copy(onboardingStep = OnboardingStep.PERMISSIONS) }
        runCurrent()

        assertEquals(
            listOf(
                SchedulerCall.Schedule(firstSchedule),
                SchedulerCall.Schedule(later),
                SchedulerCall.Cancel,
            ),
            scheduler.calls,
        )
    }

    @Test
    fun startIsIdempotentWhileTheObserverIsActive() = runTest {
        val repository = ControllableSettingsRepository(AppSettings(), initiallyReady = true)
        val scheduler = RecordingWakeupScheduler()
        val coordinator = WakeupScheduleCoordinator(repository, scheduler)

        val first = coordinator.start(backgroundScope)
        val second = coordinator.start(backgroundScope)
        runCurrent()

        assertSame(first, second)
        assertEquals(listOf(SchedulerCall.Cancel), scheduler.calls)
    }

    @Test
    fun explicitRecoveryWaitsForReadySettings() = runTest {
        val repository = ControllableSettingsRepository(
            AppSettings(onboardingStep = OnboardingStep.COMPLETE, schedule = firstSchedule),
        )
        val scheduler = RecordingWakeupScheduler()
        val coordinator = WakeupScheduleCoordinator(repository, scheduler)

        coordinator.requestReconcile(backgroundScope)
        runCurrent()
        assertTrue(scheduler.calls.isEmpty())

        repository.completeFirstLoad()
        runCurrent()

        assertEquals(listOf(SchedulerCall.Schedule(firstSchedule)), scheduler.calls)
    }

    @Test
    fun explicitRecoveryRechecksAnUnchangedSchedule() = runTest {
        val repository = ControllableSettingsRepository(
            AppSettings(onboardingStep = OnboardingStep.COMPLETE, schedule = firstSchedule),
            initiallyReady = true,
        )
        val scheduler = RecordingWakeupScheduler()
        val coordinator = WakeupScheduleCoordinator(repository, scheduler)

        coordinator.reconcileCurrent()
        coordinator.reconcileCurrent()

        assertEquals(
            listOf(
                SchedulerCall.Schedule(firstSchedule),
                SchedulerCall.Schedule(firstSchedule),
            ),
            scheduler.calls,
        )
    }

    @Test
    fun settingsAndRecoveryReconcilesCannotOverlap() = runTest {
        val repository = ControllableSettingsRepository(
            AppSettings(onboardingStep = OnboardingStep.COMPLETE, schedule = firstSchedule),
            initiallyReady = true,
        )
        val scheduler = BlockingWakeupScheduler()
        val coordinator = WakeupScheduleCoordinator(repository, scheduler)

        coordinator.requestReconcile(backgroundScope)
        runCurrent()
        coordinator.requestReconcile(backgroundScope)
        runCurrent()

        assertEquals(1, scheduler.callCount)
        assertEquals(1, scheduler.maxConcurrentCalls)

        scheduler.releaseFirstCall.complete(Unit)
        runCurrent()

        assertEquals(2, scheduler.callCount)
        assertEquals(1, scheduler.maxConcurrentCalls)
    }
}

private class ControllableSettingsRepository(
    initial: AppSettings,
    initiallyReady: Boolean = false,
) : SettingsRepository {
    private val ready = CompletableDeferred<Unit>().apply {
        if (initiallyReady) complete(Unit)
    }
    private val mutableSettings = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    override suspend fun awaitReady() {
        ready.await()
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        mutableSettings.value = transform(mutableSettings.value)
    }

    fun completeFirstLoad() {
        ready.complete(Unit)
    }
}

private sealed interface SchedulerCall {
    data class Schedule(val schedule: GlobalMorningSchedule) : SchedulerCall
    data object Cancel : SchedulerCall
}

private class RecordingWakeupScheduler : WakeupScheduler {
    val calls = mutableListOf<SchedulerCall>()
    private val mutableStatus = MutableStateFlow<WakeupScheduleStatus>(
        WakeupScheduleStatus.NotScheduled,
    )
    override val status: StateFlow<WakeupScheduleStatus> = mutableStatus.asStateFlow()

    override suspend fun schedule(settings: AppSettings): WakeupScheduleStatus {
        calls += SchedulerCall.Schedule(settings.schedule)
        mutableStatus.value = WakeupScheduleStatus.Scheduled
        return mutableStatus.value
    }

    override suspend fun cancel(): WakeupScheduleStatus {
        calls += SchedulerCall.Cancel
        mutableStatus.value = WakeupScheduleStatus.NotScheduled
        return mutableStatus.value
    }
}

private class BlockingWakeupScheduler : WakeupScheduler {
    private val mutableStatus = MutableStateFlow<WakeupScheduleStatus>(
        WakeupScheduleStatus.NotScheduled,
    )
    override val status: StateFlow<WakeupScheduleStatus> = mutableStatus.asStateFlow()
    val releaseFirstCall = CompletableDeferred<Unit>()
    var callCount = 0
        private set
    var maxConcurrentCalls = 0
        private set
    private var concurrentCalls = 0

    override suspend fun schedule(settings: AppSettings): WakeupScheduleStatus {
        concurrentCalls += 1
        maxConcurrentCalls = maxOf(maxConcurrentCalls, concurrentCalls)
        callCount += 1
        try {
            if (callCount == 1) releaseFirstCall.await()
            mutableStatus.value = WakeupScheduleStatus.Scheduled
            return mutableStatus.value
        } finally {
            concurrentCalls -= 1
        }
    }

    override suspend fun cancel(): WakeupScheduleStatus {
        mutableStatus.value = WakeupScheduleStatus.NotScheduled
        return mutableStatus.value
    }
}

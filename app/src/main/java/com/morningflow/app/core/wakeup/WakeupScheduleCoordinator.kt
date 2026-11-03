package com.morningflow.app.core.wakeup

import com.morningflow.app.core.data.SettingsRepository
import com.morningflow.app.core.data.WakeupScheduleStatus
import com.morningflow.app.core.data.WakeupScheduler
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.GlobalMorningSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The only Settings fields that can change whether or when the daily Alarm is armed. */
private data class SchedulingInput(
    val onboardingComplete: Boolean,
    val schedule: GlobalMorningSchedule,
)

/** Keeps the single production Alarm aligned with ready, persisted Settings. */
class WakeupScheduleCoordinator(
    private val settingsRepository: SettingsRepository,
    private val scheduler: WakeupScheduler,
) {
    private var observationJob: Job? = null
    private val reconcileMutex = Mutex()

    /** Starts one process-wide observer. Calling this again while it is active is a no-op. */
    fun start(scope: CoroutineScope): Job {
        observationJob?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            settingsRepository.awaitReady()
            settingsRepository.settings
                .map { SchedulingInput(it.onboardingComplete, it.schedule) }
                .distinctUntilChanged()
                .collect { input ->
                    reconcile(input)
                }
        }.also { observationJob = it }
    }

    /** Re-checks ready persisted Settings after a system or foreground recovery event. */
    fun requestReconcile(scope: CoroutineScope): Job = scope.launch {
        reconcileCurrent()
    }

    /** Waits for Settings readiness, then restores or cancels the Alarm from current state. */
    suspend fun reconcileCurrent(): WakeupScheduleStatus {
        settingsRepository.awaitReady()
        val settings = settingsRepository.settings.value
        return reconcile(SchedulingInput(settings.onboardingComplete, settings.schedule))
    }

    private suspend fun reconcile(input: SchedulingInput): WakeupScheduleStatus =
        reconcileMutex.withLock {
            if (input.onboardingComplete) {
                scheduler.schedule(AppSettings(schedule = input.schedule))
            } else {
                scheduler.cancel()
            }
        }
}

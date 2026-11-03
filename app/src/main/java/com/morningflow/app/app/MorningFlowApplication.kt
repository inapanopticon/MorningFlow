package com.morningflow.app.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.morningflow.app.core.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Owns process-wide dependencies and the weather feature's foreground lifecycle. */
class MorningFlowApplication : Application(), DefaultLifecycleObserver {
    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hasEnteredForeground = false

    override fun onCreate() {
        super<Application>.onCreate()
        appContainer = AppContainer(this)
        appContainer.wakeupScheduleCoordinator.start(applicationScope)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        appContainer.weatherLocationCoordinator.startAutoRefresh(applicationScope)
        if (hasEnteredForeground) {
            requestWakeupScheduleRecovery()
        } else {
            // The Coordinator's initial collection already reconciles a newly started process.
            hasEnteredForeground = true
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        appContainer.weatherLocationCoordinator.stopAutoRefresh()
    }

    /** Re-checks Exact Alarm capability and restores the Alarm from persisted Settings. */
    internal fun requestWakeupScheduleRecovery() =
        appContainer.wakeupScheduleCoordinator.requestReconcile(applicationScope)
}

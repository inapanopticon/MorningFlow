package com.morningflow.app.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morningflow.app.core.data.SensorBehaviourDetection
import com.morningflow.app.core.domain.AndroidSessionLightCheck
import com.morningflow.app.core.domain.SessionEngine
import com.morningflow.app.core.domain.runtime.StartResult
import com.morningflow.app.core.model.RoutineTemplate
import com.morningflow.app.core.model.SessionStatus
import com.morningflow.app.core.model.SessionUiState
import com.morningflow.app.core.sensor.BrightnessMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Holds the running session for the screens.
 *
 * The engine already owns the routine's state, so this only adds what belongs to the
 * screen rather than the routine: which template to run, and whether the exit
 * confirmation is showing.
 */
class MorningFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MorningFlowApplication).appContainer

    private val engine = SessionEngine(
        sensorRepository = container.sensorRepository,
        recordRepository = container.sessionRecordRepository,
        weatherRepository = container.weatherRepository,
        calendarRepository = container.calendarRepository,
        audioController = container.audioController,
        scope = viewModelScope,
        detection = SensorBehaviourDetection.create(application),
        lightCheck = AndroidSessionLightCheck(BrightnessMonitor(application)),
        snapshots = container.sessionSnapshotStore,
    )

    val session: StateFlow<SessionUiState> = engine.state
    val sessionRuntime = engine.runtimeState
    val templates = container.templateRepository.templates
    val settings = container.settingsRepository.settings
    val weatherLocationCoordinator = container.weatherLocationCoordinator

    private val _isExitDialogVisible = MutableStateFlow(false)
    val isExitDialogVisible: StateFlow<Boolean> = _isExitDialogVisible.asStateFlow()

    init {
        viewModelScope.launch { restoreInterruptedSession() }
    }

    /**
     * If Android ended the app mid-morning, reopening it brings the session back, paused
     * on the step it had reached. A session left for longer than [RESTORE_WINDOW] is not
     * the same morning any more, so it is dropped instead.
     */
    private suspend fun restoreInterruptedSession() {
        val store = container.sessionSnapshotStore
        val snapshot = store.load() ?: return
        container.templateRepository.awaitReady()
        container.settingsRepository.awaitReady()
        val template = selectTemplateById(templates.value, snapshot.templateId)
        val fresh = Duration.between(snapshot.savedAt, Instant.now()) < RESTORE_WINDOW
        if (template == null || !fresh || engine.restore(template, settings.value, snapshot) != StartResult.STARTED) {
            if (session.value.status !in setOf(SessionStatus.RUNNING, SessionStatus.PAUSED)) store.clear()
        }
    }

    /** Starts the exact template selected by Home or Templates. */
    fun startSession(templateId: String): Boolean {
        return startSelectedSession(templates.value, templateId,
            start = { template -> engine.start(template, settings.value) },
            onStarted = {
                // Stop the producer before cancelling its prompt. false is a safe no-op;
                // it never changes an already successful START or tomorrow's Alarm.
                container.wakeDetectionRuntimeController.stopCurrentRun()
                try {
                    container.morningPromptNotifier.cancel()
                } catch (error: Exception) {
                    // The Session already started; notification failure must not undo navigation.
                    Log.w("MorningFlowSession", "Could not cancel morning prompt after START", error)
                }
            })
    }

    fun togglePlayback() {
        when (session.value.status) {
            SessionStatus.RUNNING -> engine.pause()
            SessionStatus.PAUSED -> engine.resume()
            else -> Unit
        }
    }

    /** Whether the session was playing when the exit prompt opened, so cancelling restores it. */
    private var resumeAfterExitDialog = false

    fun requestExit() {
        // Pause first, so guidance is not still talking over the confirmation.
        resumeAfterExitDialog = session.value.status == SessionStatus.RUNNING
        engine.pause()
        _isExitDialogVisible.value = true
    }

    fun dismissExitDialog() {
        _isExitDialogVisible.value = false
        // A session the user had paused themselves stays paused.
        if (resumeAfterExitDialog) engine.resume()
    }

    fun confirmExit() {
        _isExitDialogVisible.value = false
        engine.exit()
    }

    fun skipStage() = engine.skip()

    fun returnHome() = engine.resetAfterCompletion()

    override fun onCleared() {
        super.onCleared()
        // Stops playback without ending the routine, so a session the app was closed in
        // can still be restored next time it opens.
        engine.shutDown()
        container.audioController.release()
    }

    private companion object {
        val RESTORE_WINDOW: Duration = Duration.ofHours(2)
    }
}

/** Returns only the explicitly requested template; there is deliberately no fallback selection. */
internal fun selectTemplateById(
    templates: List<RoutineTemplate>,
    templateId: String,
): RoutineTemplate? = templates.firstOrNull { it.id == templateId }

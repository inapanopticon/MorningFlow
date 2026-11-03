package com.morningflow.app.core.domain

import com.morningflow.app.R
import com.morningflow.app.core.audio.*
import com.morningflow.app.core.data.*
import com.morningflow.app.core.data.session.SessionSnapshot
import com.morningflow.app.core.data.session.SessionSnapshotStore
import com.morningflow.app.core.domain.runtime.*
import com.morningflow.app.core.domain.runtime.SessionRuntimeState
import com.morningflow.app.core.model.*
import com.morningflow.app.core.model.ModuleResult
import com.morningflow.app.core.model.SessionRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant

/** Production facade over the single Behaviour-level runtime; keeps the UI-facing session contract stable. */
class SessionEngine(
    private val sensorRepository: SensorRepository,
    private val recordRepository: SessionRecordRepository,
    private val weatherRepository: WeatherRepository,
    private val calendarRepository: CalendarRepository,
    private val audioController: AudioController,
    private val scope: CoroutineScope,
    detection: BehaviourDetection? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val lightCheck: SessionLightCheck? = null,
    private val snapshots: SessionSnapshotStore? = null,
) {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()
    private var activeSettings = AppSettings()
    private var modules = emptyList<RoutineModule>()
    private val templates = object : TemplateRepository {
        override val templates = MutableStateFlow<List<RoutineTemplate>>(emptyList())
        override suspend fun awaitReady() = Unit
        override suspend fun save(template: RoutineTemplate) = error("Session cannot modify Templates")
        override suspend fun delete(templateId: String) = error("Session cannot modify Templates")
        override suspend fun setDefault(templateId: String) = error("Session cannot modify Templates")
    }
    private var ticker: Job? = null
    /** Token of the session already written to history, so each completion is saved once. */
    private var recordedToken: Long? = null
    /** The step last written to [snapshots], so the store is only touched when it changes. */
    private var savedStepKey: String? = null
    private val snapshotWrites = Mutex()
    private var snapshotsFrozen = false
    private val playback = Playback()
    private val runtime = SessionRuntime(templates, clock, playback, detection)
    val runtimeState: StateFlow<SessionRuntimeState> = runtime.state

    init {
        if (detection is SensorBehaviourDetection) detection.report = { token, count, available, failed ->
            runtime.onSensor(token, count, available, failed)
            publish()
        }
    }

    fun start(template: RoutineTemplate, settings: AppSettings): StartResult {
        if (runtime.state.value.status in setOf(SessionStatus.RUNNING, SessionStatus.PAUSED)) return StartResult.ALREADY_ACTIVE
        val enabled = template.modules.filter { it.enabled && it.behaviours.any(Behaviour::enabled) }.sortedBy { it.type.ordinal }
        if (enabled.isEmpty()) return StartResult.EMPTY_TEMPLATE
        // Disable the legacy combined sensors. Only the Behaviour adapter owns counting now.
        sensorRepository.stop()
        modules = enabled
        activeSettings = settings
        templates.templates.value = listOf(template)
        val result = runtime.start(template.id, settings)
        if (result == StartResult.STARTED) {
            if (settings.audio.backgroundMusicEnabled) {
                safeAudio { audioController.startBackgroundMusic(settings.audio.backgroundMusicVolume) }
            }
            startTicker()
        }
        publish()
        return result
    }

    /**
     * Brings back a session Android ended in the background, paused at the start of the
     * step it had reached. Nothing plays until the user resumes.
     */
    fun restore(template: RoutineTemplate, settings: AppSettings, snapshot: SessionSnapshot): StartResult {
        if (runtime.state.value.status in setOf(SessionStatus.RUNNING, SessionStatus.PAUSED)) return StartResult.ALREADY_ACTIVE
        val enabled = template.modules.filter { it.enabled && it.behaviours.any(Behaviour::enabled) }.sortedBy { it.type.ordinal }
        if (enabled.isEmpty()) return StartResult.EMPTY_TEMPLATE
        sensorRepository.stop()
        modules = enabled
        activeSettings = settings
        templates.templates.value = listOf(template)
        val result = runtime.restore(template.id, settings, snapshot.stepIndex, snapshot.startedAt)
        if (result == StartResult.STARTED) startTicker()
        publish()
        return result
    }

    private fun startTicker() {
        ticker = scope.launch {
            while (isActive && runtime.state.value.status in setOf(SessionStatus.RUNNING, SessionStatus.PAUSED)) {
                delay(100); runtime.tick(); publish()
            }
        }
    }

    fun pause() { runtime.pause(); safeAudio { audioController.stopBackgroundMusic() }; publish() }
    fun resume() {
        if (runtime.state.value.status != SessionStatus.PAUSED) return
        runtime.resume()
        if (activeSettings.audio.backgroundMusicEnabled) {
            safeAudio { audioController.startBackgroundMusic(activeSettings.audio.backgroundMusicVolume) }
        }
        publish()
    }
    /** Existing complete control means Skip, except Leaving Reminder where it means FINISH. */
    fun skip() {
        val current = runtime.state.value
        if (current.isLeaving) runtime.finish(current.token) else runtime.skip(current.token)
        publish()
    }
    fun exit() { runtime.exit(); sensorRepository.stop(); safeAudio { audioController.stopBackgroundMusic() }; publish() }
    /**
     * Stops all playback and sensors for good without ending the routine, so the saved
     * snapshot survives and the session can be restored when the app is opened again.
     */
    fun shutDown() {
        snapshotsFrozen = true
        exit()
    }

    fun resetAfterCompletion() {
        if (runtime.state.value.status in setOf(SessionStatus.RUNNING, SessionStatus.PAUSED)) return
        runtime.clearCompletion(); modules = emptyList(); publish()
    }

    private fun safeAudio(effect: () -> Unit) {
        try { effect() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { runtime.onAudioFailure(runtime.state.value.token) }
    }

    private fun publish() {
        val current = runtime.state.value
        val step = current.current
        val moduleIndex = if (step == null) {
            if (current.status == SessionStatus.COMPLETED) modules.size else 0
        } else modules.indexOfFirst { it.type == step.module }.coerceAtLeast(0)
        val behaviourIndex = modules.getOrNull(moduleIndex)?.behaviours?.filter(Behaviour::enabled)
            ?.indexOfFirst { it.id == step?.behaviour?.id }?.coerceAtLeast(0) ?: 0
        val results = modules.mapNotNull { module ->
            val outcomes = current.outcomes.filter { it.step.module == module.type }
            if (outcomes.size != module.behaviours.count(Behaviour::enabled)) null
            else ModuleResult(module.type, when {
                outcomes.any { it.kind == CompletionKind.SKIPPED } -> CompletionKind.SKIPPED
                outcomes.any { it.kind == CompletionKind.TIMED } -> CompletionKind.TIMED
                outcomes.all { it.kind == CompletionKind.VERIFIED } -> CompletionKind.VERIFIED
                else -> CompletionKind.GUIDANCE
            })
        }
        _state.value = SessionUiState(status = current.status, templateName = current.templateName,
            modules = modules, currentModuleIndex = moduleIndex, currentBehaviourIndex = behaviourIndex,
            remainingSeconds = current.remainingSeconds.toInt(), elapsedSeconds = current.elapsedSeconds.toInt(),
            results = results, sensor = SensorSnapshot(
                squatCount = if (step?.behaviour?.key == BehaviourKey.SQUATS) current.progress else 0,
                stepCount = if (step?.behaviour?.key == BehaviourKey.WALKING) current.progress else 0))
        if (current.status in setOf(SessionStatus.COMPLETED, SessionStatus.EXITED)) {
            ticker?.cancel(); ticker = null
            safeAudio { audioController.stopBackgroundMusic() }
        }
        rememberStep(current)
        if (current.status == SessionStatus.COMPLETED && recordedToken != current.token) {
            recordedToken = current.token
            saveRecord(current, results)
        }
    }

    /** Keeps [snapshots] on the current step while a session is open, and empty otherwise. */
    private fun rememberStep(state: SessionRuntimeState) {
        val store = snapshots ?: return
        if (snapshotsFrozen) return
        val open = state.status in setOf(SessionStatus.RUNNING, SessionStatus.PAUSED)
        val startedAt = state.startedAt
        val key = if (open && startedAt != null) "${state.templateId}/$startedAt/${state.index}" else null
        if (key == savedStepKey) return
        savedStepKey = key
        val snapshot = if (key == null || startedAt == null) null
            else SessionSnapshot(state.templateId, state.index, startedAt, clock.instant())
        scope.launch {
            // A lost snapshot only means no restore, so it must never disturb the session.
            try {
                snapshotWrites.withLock { if (snapshot == null) store.clear() else store.save(snapshot) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
        }
    }

    /** History feeds the streak on Home; a failed write must never disturb the session. */
    private fun saveRecord(state: SessionRuntimeState, results: List<ModuleResult>) {
        val endedAt = state.finishedAt ?: Instant.now()
        val record = SessionRecord(
            templateId = state.templateId,
            templateName = state.templateName,
            startedAt = state.startedAt ?: endedAt.minusSeconds(state.elapsedSeconds),
            endedAt = endedAt,
            actualDurationSeconds = state.elapsedSeconds.toInt(),
            status = SessionStatus.COMPLETED,
            results = results,
        )
        scope.launch {
            try {
                recordRepository.save(record)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Losing one history entry is better than failing the finished session.
            }
        }
    }

    private inner class Playback : SessionPlayback {
        private var behaviour: Behaviour? = null
        private var settings = AudioSettings()
        private var token = 0L
        private var job: Job? = null
        private var segments: List<NarrationSegment>? = null
        private var index = 0
        private var pauseLeft = 0L
        private var pauseStarted = 0L
        private var lightChecked = false
        private var lightReading: Boolean? = null
        private var praiseFor: BehaviourKey? = null
        override fun begin(behaviour: Behaviour, settings: AudioSettings, token: Long) {
            stop()
            this.behaviour = behaviour; this.settings = settings; this.token = token
            segments = null; index = 0; pauseLeft = 0; lightChecked = false; lightReading = null
            // Squats and walking are quiet until their target, so their "nice work" is said
            // at the start of whatever comes next.
            praiseFor = runtime.state.value.outcomes.lastOrNull()
                ?.takeIf { it.kind == CompletionKind.VERIFIED && it.step.behaviour.key.isSensor }
                ?.step?.behaviour?.key
            launch()
        }
        private fun launch() {
            job = scope.launch {
                val activeToken = token
                try {
                    waitPause()
                    val key = behaviour!!.key
                    if (!lightChecked && key == BehaviourKey.TURN_ON_LIGHT) {
                        lightReading = try { lightCheck?.read() }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                        lightChecked = true
                    }
                    if (!settings.voiceGuidanceEnabled) return@launch
                    val lines = segments ?: segmentsFor(behaviour!!).let { guidance ->
                        // Only confirmed brightness suppresses the opening-light instruction.
                        // Dark/no-reading uses the normal script once, without an extra reminder.
                        if (key == BehaviourKey.TURN_ON_LIGHT && lightReading == true) {
                            guidance.filterNot { it.clipRes == R.raw.turn_on_light_02 }
                        } else guidance
                    }.let { guidance ->
                        praiseFor?.let { NarrationScripts.afterSensorTarget(it, guidance) } ?: guidance
                    }.also { segments = it }
                    while (index < lines.size) {
                        val line = lines[index]
                        audioController.playSegment(line, settings.voiceGuidanceVolume)
                        index++
                        pauseLeft = line.pauseAfterMillis
                        waitPause()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { runtime.onAudioFailure(activeToken); publish() }
            }
        }
        private suspend fun waitPause() {
            if (pauseLeft <= 0) return
            pauseStarted = System.nanoTime()
            try { delay(pauseLeft); pauseLeft = 0 }
            finally {
                if (pauseLeft > 0) pauseLeft = (pauseLeft - (System.nanoTime() - pauseStarted) / 1_000_000).coerceAtLeast(0)
            }
        }
        override fun pause() { job?.cancel(); job = null; audioController.stopGuidance() }
        override fun resume() { launch() }
        override fun stop() { job?.cancel(); job = null; audioController.stopGuidance() }
        override fun cue() { audioController.playCue(true) }
    }
    private suspend fun segmentsFor(behaviour: Behaviour): List<NarrationSegment> =
        if (behaviour.key == BehaviourKey.LEAVING_REMINDER) {
            supervisorScope {
                // Independent sources: slow or failed weather must not prevent Calendar narration.
                val weather = async { reminderData { weatherRepository.getTodayWeather() } }
                val calendar = async { reminderData { calendarRepository.getRemainingTodayEvents() } }
                LeavingReminderNarration.build(
                    settings = activeSettings,
                    weather = (weather.await() as? DataResult.Success)?.value,
                    events = calendar.await(),
                )
            }
        } else {
            NarrationScripts[behaviour.key, behaviour.mode]
        }

    private suspend fun <T> reminderData(query: suspend () -> DataResult<T>): DataResult<T> = try {
        withTimeoutOrNull(5_000) { query() } ?: DataResult.Unavailable("QUERY_FAILED")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DataResult.Unavailable("QUERY_FAILED")
    }

}

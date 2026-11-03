package com.morningflow.app.core.domain.runtime

import com.morningflow.app.core.data.TemplateRepository
import com.morningflow.app.core.model.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Audio boundary for a session. Implementations must pause/resume all session audio, preserving position. */
interface SessionPlayback {
    fun begin(behaviour: Behaviour, settings: AudioSettings, token: Long)
    fun pause()
    fun resume()
    fun stop()
    fun cue()
}

/** Optional hardware adapter. Never translate absent hardware into a successful reading. */
interface BehaviourDetection {
    fun begin(key: BehaviourKey, token: Long)
    fun pause()
    fun resume()
    fun stop()
}

data class RuntimeStep(val module: ModuleType, val behaviour: Behaviour, val seconds: Int, val target: Int)
data class BehaviourOutcome(val step: RuntimeStep, val kind: CompletionKind, val endedAt: Instant)
data class SessionRuntimeState(
    val status: SessionStatus = SessionStatus.NOT_STARTED,
    val templateId: String = "",
    val templateName: String = "",
    val steps: List<RuntimeStep> = emptyList(),
    val index: Int = 0,
    val token: Long = 0,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val remainingMillis: Long = 0,
    val elapsedSeconds: Long = 0,
    val progress: Int = 0,
    val sensorAvailable: Boolean = false,
    val pleaseTryAgain: Boolean = false,
    val audioUnavailable: Boolean = false,
    val outcomes: List<BehaviourOutcome> = emptyList(),
) {
    val current: RuntimeStep? get() = steps.getOrNull(index)
    val isLeaving: Boolean get() = current?.behaviour?.key == BehaviourKey.LEAVING_REMINDER
    val remainingSeconds: Long get() = (remainingMillis + 999) / 1000
}

enum class StartResult { STARTED, ALREADY_ACTIVE, TEMPLATE_NOT_FOUND, EMPTY_TEMPLATE }

/**
 * Core session state machine, driven through [SessionEngine]. Call all methods on the owning
 * dispatcher (normally Main). tick() may also be driven by lifecycle catch-up or a test clock.
 * No history repository, database writes, or template mutations. Results exist in memory only.
 */
class SessionRuntime(
    private val templates: TemplateRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val playback: SessionPlayback? = null,
    private val detection: BehaviourDetection? = null,
) {
    private val mutable = MutableStateFlow(SessionRuntimeState())
    val state = mutable.asStateFlow()
    private var revision = 0L
    private var deadline: Instant? = null
    private var audio = AudioSettings()

    fun runTicker(scope: CoroutineScope): Job = scope.launch {
        while (isActive) { delay(100); tick() }
    }

    fun start(templateId: String, settings: AppSettings): StartResult {
        if (active()) return StartResult.ALREADY_ACTIVE
        val template = templates.templates.value.firstOrNull { it.id == templateId }
            ?: return StartResult.TEMPLATE_NOT_FOUND
        val steps = stepsOf(template)
        if (steps.isEmpty()) return StartResult.EMPTY_TEMPLATE
        audio = settings.audio.copy()
        val now = clock.instant()
        mutable.value = SessionRuntimeState(status = SessionStatus.RUNNING, templateId = template.id,
            templateName = template.name, steps = steps.toList(), startedAt = now)
        begin(now)
        return StartResult.STARTED
    }

    /**
     * Puts a session that Android ended in the background back on the step it had reached,
     * paused and from the start of that step, so nothing plays until the user resumes.
     */
    fun restore(templateId: String, settings: AppSettings, stepIndex: Int, startedAt: Instant): StartResult {
        if (active()) return StartResult.ALREADY_ACTIVE
        val template = templates.templates.value.firstOrNull { it.id == templateId }
            ?: return StartResult.TEMPLATE_NOT_FOUND
        val steps = stepsOf(template)
        if (stepIndex !in steps.indices) return StartResult.EMPTY_TEMPLATE
        audio = settings.audio.copy()
        mutable.value = SessionRuntimeState(status = SessionStatus.PAUSED, templateId = template.id,
            templateName = template.name, steps = steps, index = stepIndex, startedAt = startedAt,
            token = ++revision, remainingMillis = steps[stepIndex].seconds.coerceAtLeast(0) * 1000L)
        deadline = null
        // The step has not begun its narration or sensor yet; resume() will begin it.
        needsActivation = true
        return StartResult.STARTED
    }

    private fun stepsOf(template: RoutineTemplate): List<RuntimeStep> =
        template.modules.filter { it.enabled }.sortedBy { it.type.ordinal }.flatMap { m ->
            m.behaviours.filter { it.enabled }.map { b ->
                RuntimeStep(m.type, b.copy(), b.plannedSeconds(m.squatTarget, m.stepTarget),
                    when (b.key) { BehaviourKey.SQUATS -> m.squatTarget; BehaviourKey.WALKING -> m.stepTarget; else -> 0 })
            }
        }

    fun tick() {
        if (mutable.value.status != SessionStatus.RUNNING) return
        val now = clock.instant()
        // Catch up before reading live sensor events; never infer movement during a gap.
        while (mutable.value.status == SessionStatus.RUNNING && deadline?.let { !now.isBefore(it) } == true) {
            complete(CompletionKind.TIMED, deadline!!, activate = false)
        }
        if (!active()) return
        updateTime(now)
        activateIfNeeded()
    }

    fun skip(token: Long) {
        if (mutable.value.status != SessionStatus.RUNNING) return
        tick()
        if (token != mutable.value.token || !active() || mutable.value.isLeaving) return
        complete(CompletionKind.SKIPPED, clock.instant())
    }

    /** cumulativeCount is confirmed count for this behaviour, preserved by the adapter on resume. */
    fun onSensor(token: Long, cumulativeCount: Int, available: Boolean, recognitionFailed: Boolean = false) {
        if (mutable.value.status != SessionStatus.RUNNING) return
        tick()
        val s = mutable.value
        val step = s.current ?: return
        if (!active() || token != s.token || !step.behaviour.key.isSensor) return
        val count = if (available) maxOf(s.progress, cumulativeCount.coerceAtLeast(0)) else s.progress
        mutable.value = s.copy(progress = count, sensorAvailable = available, pleaseTryAgain = available && recognitionFailed)
        if (available && step.target > 0 && count >= step.target) complete(CompletionKind.VERIFIED, clock.instant())
    }

    fun pause() {
        if (mutable.value.status != SessionStatus.RUNNING) return
        tick()
        if (!active()) return
        deadline = null
        mutable.value = mutable.value.copy(status = SessionStatus.PAUSED)
        sensorEffect { detection?.pause() }
        audioEffect { playback?.pause() }
    }

    fun resume() {
        val s = mutable.value
        if (s.status != SessionStatus.PAUSED) return
        mutable.value = s.copy(status = SessionStatus.RUNNING)
        deadline = if (s.isLeaving) null else clock.instant().plusMillis(s.remainingMillis)
        if (needsActivation) {
            // A restored step has never started, so it begins rather than continues.
            activateIfNeeded()
        } else {
            if (s.current?.behaviour?.key?.isSensor == true) sensorEffect { detection?.resume() }
            audioEffect { playback?.resume() }
        }
        updateTime(clock.instant())
    }

    fun finish(token: Long) {
        if (mutable.value.status != SessionStatus.RUNNING || token != mutable.value.token || !mutable.value.isLeaving) return
        complete(CompletionKind.GUIDANCE, clock.instant())
    }

    fun exit() { if (active()) end(SessionStatus.EXITED, clock.instant()) }

    fun clearCompletion() {
        if (active()) return
        mutable.value = SessionRuntimeState(token = ++revision)
    }

    fun onAudioFailure(token: Long) {
        if (active() && token == mutable.value.token) mutable.value = mutable.value.copy(audioUnavailable = true)
    }

    private var needsActivation = false
    private fun begin(at: Instant, activate: Boolean = true) {
        val s = mutable.value
        val millis = s.current!!.seconds.coerceAtLeast(0) * 1000L
        mutable.value = s.copy(token = ++revision, remainingMillis = millis, progress = 0,
            sensorAvailable = false, pleaseTryAgain = false, audioUnavailable = false)
        deadline = if (mutable.value.isLeaving) null else at.plusMillis(millis)
        needsActivation = true
        if (activate) activateIfNeeded()
    }

    private fun activateIfNeeded() {
        if (!needsActivation || mutable.value.status != SessionStatus.RUNNING) return
        needsActivation = false
        val s = mutable.value
        val step = s.current ?: return
        if (step.behaviour.key.isSensor) sensorEffect { detection?.begin(step.behaviour.key, s.token) }
        audioEffect { playback?.begin(step.behaviour, audio, s.token) }
    }

    private fun complete(kind: CompletionKind, at: Instant, activate: Boolean = true) {
        val s = mutable.value
        stopResources()
        mutable.value = s.copy(index = s.index + 1, outcomes = s.outcomes + BehaviourOutcome(s.current!!, kind, at))
        if (mutable.value.current == null) end(SessionStatus.COMPLETED, at)
        else begin(at, activate)
        if (activate && audio.audioCuesEnabled) audioEffect { playback?.cue() }
    }

    private fun end(status: SessionStatus, at: Instant) {
        stopResources()
        deadline = null
        needsActivation = false
        mutable.value = mutable.value.copy(status = status, token = ++revision, finishedAt = at, remainingMillis = 0,
            elapsedSeconds = Duration.between(mutable.value.startedAt, at).seconds.coerceAtLeast(0))
    }

    private fun updateTime(now: Instant) {
        mutable.value = mutable.value.copy(remainingMillis = deadline?.let { Duration.between(now, it).toMillis().coerceAtLeast(0) } ?: 0,
            elapsedSeconds = Duration.between(mutable.value.startedAt, now).seconds.coerceAtLeast(0))
    }
    private fun active() = mutable.value.status == SessionStatus.RUNNING || mutable.value.status == SessionStatus.PAUSED
    private fun stopResources() { sensorEffect { detection?.stop() }; audioEffect { playback?.stop() } }
    private fun audioEffect(block: () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.value = mutable.value.copy(audioUnavailable = true) }
    }
    private fun sensorEffect(block: () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.value = mutable.value.copy(sensorAvailable = false) }
    }
}

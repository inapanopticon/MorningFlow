package com.morningflow.app.core.domain.runtime

import com.morningflow.app.core.data.TemplateRepository
import com.morningflow.app.core.model.*
import java.time.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

class SessionRuntimeTest {
    private class Time : Clock() {
        var now = Instant.parse("2026-09-30T07:00:00Z")
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        fun advance(seconds: Long) { now = now.plusSeconds(seconds) }
    }
    private class Templates(template: RoutineTemplate) : TemplateRepository {
        override val templates = MutableStateFlow(listOf(template))
        override suspend fun awaitReady() = Unit
        override suspend fun save(template: RoutineTemplate) { error("runtime must not save") }
        override suspend fun delete(templateId: String) { error("runtime must not delete") }
        override suspend fun setDefault(templateId: String) { error("runtime must not set default") }
    }
    private class Effects : SessionPlayback, BehaviourDetection {
        var pauses = 0; var resumes = 0; var stops = 0; var fail = false
        val begun = mutableListOf<BehaviourKey>()
        override fun begin(behaviour: Behaviour, settings: AudioSettings, token: Long) { if (fail) error("audio unavailable"); begun += behaviour.key }
        override fun begin(key: BehaviourKey, token: Long) = Unit
        override fun pause() { pauses++ }
        override fun resume() { resumes++ }
        override fun stop() { stops++ }
        override fun cue() = Unit
    }
    private fun template(vararg keys: BehaviourKey) = RoutineTemplate(id = "user-template", name = "Test",
        modules = keys.toList().groupBy { it.module }.map { (m, b) -> RoutineModule(type = m,
            behaviours = b.map { Behaviour(key = it) }, squatTarget = 2, stepTarget = 10) })
    private class Fixture(t: RoutineTemplate) {
        val time = Time(); val repo = Templates(t); val effects = Effects()
        val engine = SessionRuntime(repo, time, effects, effects)
        val state get() = engine.state.value
        fun start() = engine.start("user-template", AppSettings())
        fun advance(s: Long) { time.advance(s); engine.tick() }
    }
    @Test fun startsByIdAndCopiesSharedTimingWithoutMutatingTemplate() {
        val t = template(BehaviourKey.WALKING); val f = Fixture(t)
        assertEquals(StartResult.TEMPLATE_NOT_FOUND, f.engine.start("missing", AppSettings()))
        assertEquals(StartResult.STARTED, f.start())
        assertEquals(t.plannedSeconds.toLong(), f.state.remainingSeconds)
        f.repo.templates.value = emptyList()
        assertEquals(BehaviourKey.WALKING, f.state.current!!.behaviour.key)
        assertEquals(StartResult.ALREADY_ACTIVE, f.start())
    }
    @Test fun filtersDisabledContentAndSortsModules() {
        val t = template(BehaviourKey.WALKING, BehaviourKey.LEAVE_BED, BehaviourKey.LIGHT_REMINDER)
        val f = Fixture(t.copy(modules = t.modules.map { m -> if (m.type == ModuleType.PHYSICAL_WAKE_UP) m.copy(enabled = false)
            else m.copy(behaviours = m.behaviours.map { if (it.key == BehaviourKey.LIGHT_REMINDER) it.copy(mode = BehaviourMode.OFF) else it }) }))
        f.start(); assertEquals(listOf(BehaviourKey.LEAVE_BED), f.state.steps.map { it.behaviour.key })
    }
    @Test fun timerAdvancesOneBehaviourAndCompletionStaysInMemory() {
        val f = Fixture(template(BehaviourKey.LEAVE_BED, BehaviourKey.CHANGE_INTO_HOME_CLOTHES)); f.start()
        f.advance(40); assertEquals(BehaviourKey.CHANGE_INTO_HOME_CLOTHES, f.state.current!!.behaviour.key)
        f.advance(30); assertEquals(SessionStatus.COMPLETED, f.state.status)
        assertEquals(70L, f.state.elapsedSeconds); assertEquals(2, f.state.outcomes.size)
        assertEquals(SessionStatus.NOT_STARTED, SessionRuntime(f.repo, f.time).state.value.status)
        f.engine.clearCompletion(); assertTrue(f.state.outcomes.isEmpty())
    }
    @Test fun skipAndSensorRaceAcceptOnlyFirstTerminal() {
        val f = Fixture(template(BehaviourKey.WALKING, BehaviourKey.SQUATS)); f.start(); val old = f.state.token
        f.engine.skip(old); f.engine.onSensor(old, 10, true)
        assertEquals(1, f.state.outcomes.size); assertEquals(CompletionKind.SKIPPED, f.state.outcomes.single().kind)
        assertEquals(0, f.state.progress)
    }
    @Test fun verifiedThenStaleSkipDoesNotSkipNext() {
        val f = Fixture(template(BehaviourKey.WALKING, BehaviourKey.SQUATS)); f.start(); val old = f.state.token
        f.engine.onSensor(old, 10, true); f.engine.skip(old)
        assertEquals(1, f.state.outcomes.size); assertEquals(CompletionKind.VERIFIED, f.state.outcomes.single().kind)
    }
    @Test fun expiredDeadlineWinsOverSkipOrSensor() {
        for (sensor in listOf(false, true)) {
            val f = Fixture(template(BehaviourKey.WALKING, BehaviourKey.SQUATS)); f.start(); val old = f.state.token
            f.time.advance(f.state.remainingSeconds)
            if (sensor) f.engine.onSensor(old, 10, true) else f.engine.skip(old)
            assertEquals(1, f.state.outcomes.size); assertEquals(CompletionKind.TIMED, f.state.outcomes.single().kind)
        }
    }
    @Test fun unavailablePreservesCountAndRecoveryCanVerify() {
        val f = Fixture(template(BehaviourKey.WALKING)); f.start(); val token = f.state.token
        f.engine.onSensor(token, 4, true); f.engine.onSensor(token, 999, false)
        assertEquals(4, f.state.progress); assertFalse(f.state.sensorAvailable)
        f.engine.onSensor(token, 4, true, true); assertTrue(f.state.pleaseTryAgain)
        f.engine.onSensor(token, 10, true); assertEquals(CompletionKind.VERIFIED, f.state.outcomes.single().kind)
    }
    @Test fun unavailableTimesOutWithoutVerification() {
        val f = Fixture(template(BehaviourKey.SQUATS)); f.start()
        f.engine.onSensor(f.state.token, 100, false); f.advance(f.state.remainingSeconds)
        assertEquals(CompletionKind.TIMED, f.state.outcomes.single().kind)
    }
    @Test fun pauseFreezesTimeAndCountWhileElapsedIncludesPause() {
        val f = Fixture(template(BehaviourKey.WALKING)); f.start(); val token = f.state.token
        f.engine.onSensor(token, 4, true); f.advance(5); val remaining = f.state.remainingSeconds
        f.engine.pause(); f.advance(100); f.engine.onSensor(token, 10, true); f.engine.skip(token)
        assertEquals(remaining, f.state.remainingSeconds); assertEquals(4, f.state.progress)
        f.engine.resume(); f.advance(remaining)
        assertEquals(100L + f.state.steps.single().seconds, f.state.elapsedSeconds)
        assertTrue(f.effects.pauses > 0); assertTrue(f.effects.resumes > 0)
    }
    @Test fun leavingOnlyFinishesWithFinishAndExitDoesNotComplete() {
        val f = Fixture(template(BehaviourKey.LEAVING_REMINDER)); f.start(); val token = f.state.token
        f.advance(1000); f.engine.skip(token); assertEquals(SessionStatus.RUNNING, f.state.status)
        f.engine.finish(token); f.engine.finish(token); assertEquals(1, f.state.outcomes.size)
        assertEquals(CompletionKind.GUIDANCE, f.state.outcomes.single().kind)
        f.start(); f.engine.exit(); assertEquals(SessionStatus.EXITED, f.state.status)
        assertTrue(f.state.outcomes.isEmpty())
    }
    @Test fun audioErrorsDoNotStopTimersAndOldFailureIsIgnored() {
        val f = Fixture(template(BehaviourKey.LEAVE_BED, BehaviourKey.CHANGE_INTO_HOME_CLOTHES)); f.effects.fail = true
        f.start(); assertTrue(f.state.audioUnavailable); val old = f.state.token
        f.effects.fail = false; f.advance(40); f.engine.onAudioFailure(old)
        assertFalse(f.state.audioUnavailable); f.advance(30); assertEquals(SessionStatus.COMPLETED, f.state.status)
    }
    @Test fun catchUpDoesNotPlayExpiredNarrations() {
        val f = Fixture(template(BehaviourKey.LEAVE_BED, BehaviourKey.CHANGE_INTO_HOME_CLOTHES, BehaviourKey.LEAVING_REMINDER)); f.start()
        f.advance(100)
        assertEquals(listOf(BehaviourKey.LEAVE_BED, BehaviourKey.LEAVING_REMINDER), f.effects.begun)
        assertEquals(2, f.state.outcomes.size)
    }
    @Test fun emptyTemplateIsRejected() {
        val f = Fixture(template()); assertEquals(StartResult.EMPTY_TEMPLATE, f.start())
        assertEquals(SessionStatus.NOT_STARTED, f.state.status)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun finishDoesNotWaitForPendingCalendarQuery() = kotlinx.coroutines.test.runTest {
        val f = Fixture(template(BehaviourKey.LEAVING_REMINDER))
        f.start()
        val loader = LeavingReminderLoader(object : com.morningflow.app.core.data.WeatherRepository {
            override suspend fun getTodayWeather(): DataResult<WeatherInfo> = DataResult.Unavailable("offline")
        }, object : com.morningflow.app.core.data.CalendarRepository {
            override suspend fun getRemainingTodayEvents(): DataResult<List<com.morningflow.app.core.model.calendar.CalendarEvent>> =
                kotlinx.coroutines.awaitCancellation()
        }, location = { DataResult.Unavailable("offline") })
        val loading = async { loader.load(AppSettings()) }
        runCurrent()
        assertFalse(loading.isCompleted)
        f.engine.finish(f.state.token)
        assertEquals(SessionStatus.COMPLETED, f.state.status)
        loading.cancel()
        loading.join()
        assertTrue(loading.isCancelled)
    }

}

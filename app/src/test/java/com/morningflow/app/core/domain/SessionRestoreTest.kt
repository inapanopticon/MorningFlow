package com.morningflow.app.core.domain

import com.morningflow.app.core.audio.*
import com.morningflow.app.core.data.*
import com.morningflow.app.core.data.session.InMemorySessionSnapshotStore
import com.morningflow.app.core.data.session.SessionSnapshot
import com.morningflow.app.core.domain.runtime.StartResult
import com.morningflow.app.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * If Android ends the app mid-morning, the session comes back on the step it had
 * reached, paused, and only starts talking again once the user resumes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRestoreTest {
    private class RecordingAudio : AudioController {
        val played = mutableListOf<NarrationSegment>()
        override fun startBackgroundMusic(volume: Float) = Unit
        override fun stopBackgroundMusic() = Unit
        override suspend fun playSegment(segment: NarrationSegment, volume: Float) { played += segment }
        override suspend fun speak(text: String, volume: Float) = Unit
        override fun stopGuidance() = Unit
        override fun playCue(enabled: Boolean) = Unit
        override fun release() = Unit
    }

    private class StillSensor : CountingSensor {
        override val count = MutableStateFlow(0)
        override val isAvailable = true
        override fun start() = Unit
        override fun pause() = Unit
        override fun stop() = Unit
        override fun reset() { count.value = 0 }
    }

    private val store = InMemorySessionSnapshotStore()

    private fun TestScope.engine(audio: AudioController = RecordingAudio()) = SessionEngine(
        object : SensorRepository {
            override val snapshot = MutableStateFlow(SensorSnapshot())
            override fun startDemoSignals() = Unit
            override fun pause() = Unit
            override fun stop() = Unit
            override fun reset() = Unit
        },
        object : SessionRecordRepository {
            override val records = MutableStateFlow<List<SessionRecord>>(emptyList())
            override suspend fun save(record: SessionRecord) = Unit
        },
        object : WeatherRepository {
            override suspend fun getTodayWeather(): DataResult<WeatherInfo> = DataResult.Unavailable("offline")
        },
        FakeCalendarRepository(),
        audio,
        this,
        detection = SensorBehaviourDetection(StillSensor(), StillSensor(), this),
        snapshots = store,
    )

    private val template = RoutineTemplate(
        id = "restore-test",
        name = "Restore test",
        modules = listOf(
            RoutineModule(type = BehaviourKey.LEAVE_BED.module, behaviours = listOf(Behaviour(key = BehaviourKey.LEAVE_BED))),
            RoutineModule(type = BehaviourKey.BRUSH_TEETH.module, behaviours = listOf(Behaviour(key = BehaviourKey.BRUSH_TEETH))),
        ),
    )

    private val settings = AppSettings(audio = AudioSettings(backgroundMusicEnabled = false))

    @Test
    fun theCurrentStepIsSavedAsTheSessionMovesOn() = runTest {
        val engine = engine()
        engine.start(template, settings); runCurrent()
        assertEquals(0, store.load()?.stepIndex)
        assertEquals("restore-test", store.load()?.templateId)

        engine.skip(); runCurrent()
        assertEquals(1, store.load()?.stepIndex)
        engine.exit(); runCurrent()
    }

    @Test
    fun endingTheSessionForgetsIt() = runTest {
        val engine = engine()
        engine.start(template, settings); runCurrent()

        engine.exit(); runCurrent()

        assertNull(store.load())
    }

    @Test
    fun closingTheAppKeepsTheSessionToRestore() = runTest {
        val engine = engine()
        engine.start(template, settings); runCurrent()

        engine.shutDown(); runCurrent()

        assertEquals(0, store.load()?.stepIndex)
    }

    @Test
    fun aRestoredSessionWaitsPausedOnItsStepUntilResumed() = runTest {
        val audio = RecordingAudio()
        val engine = engine(audio)
        val startedAt = Instant.parse("2026-11-02T20:00:00Z")

        val result = engine.restore(template, settings, SessionSnapshot("restore-test", 1, startedAt, startedAt))
        runCurrent()

        assertEquals(StartResult.STARTED, result)
        assertEquals(SessionStatus.PAUSED, engine.state.value.status)
        assertEquals(BehaviourKey.BRUSH_TEETH, engine.runtimeState.value.current?.behaviour?.key)
        assertEquals(BehaviourKey.BRUSH_TEETH.mediumSeconds, engine.state.value.remainingSeconds)
        assertTrue("nothing should play before the user resumes", audio.played.isEmpty())

        engine.resume(); runCurrent()

        assertEquals(SessionStatus.RUNNING, engine.state.value.status)
        assertEquals(NarrationScripts[BehaviourKey.BRUSH_TEETH].first().clipRes, audio.played.firstOrNull()?.clipRes)
        engine.exit(); runCurrent()
    }

    @Test
    fun aStepThatNoLongerExistsIsNotRestored() = runTest {
        val engine = engine()
        val now = Instant.now()

        val result = engine.restore(template, settings, SessionSnapshot("restore-test", 5, now, now))

        assertNotEquals(StartResult.STARTED, result)
        assertEquals(SessionStatus.NOT_STARTED, engine.state.value.status)
    }
}

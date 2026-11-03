package com.morningflow.app.core.domain

import com.morningflow.app.core.audio.*
import com.morningflow.app.core.data.*
import com.morningflow.app.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSensorIntegrationTest {
    private class Counter : CountingSensor {
        override val count = MutableStateFlow(0)
        var available = true
        override val isAvailable get() = available
        var running = false
        var resets = 0
        override fun start() { running = true }
        override fun pause() { running = false }
        override fun stop() { running = false }
        override fun reset() { running = false; count.value = 0; resets++ }
        fun move(n: Int) { if (running) count.value += n }
    }
    private class Audio : AudioController {
        val lines = mutableListOf<String>()
        override fun startBackgroundMusic(volume: Float) = Unit
        override fun stopBackgroundMusic() = Unit
        override suspend fun playSegment(segment: NarrationSegment, volume: Float) { lines += segment.text }
        override suspend fun speak(text: String, volume: Float) = Unit
        override fun stopGuidance() = Unit
        override fun playCue(enabled: Boolean) = Unit
        override fun release() = Unit
    }
    private class Fixture(scope: TestScope, light: SessionLightCheck? = null) {
        val squat = Counter(); val walking = Counter(); val audio = Audio()
        var legacyStarts = 0; var saved = 0
        private val clock = object : Clock() {
            override fun instant() = Instant.parse("2026-10-06T00:00:00Z").plusMillis(scope.testScheduler.currentTime)
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
        }
        val engine = SessionEngine(object : SensorRepository {
            override val snapshot = MutableStateFlow(SensorSnapshot())
            override fun startDemoSignals() { legacyStarts++ }
            override fun pause() = Unit
            override fun stop() = Unit
            override fun reset() = Unit
        }, object : SessionRecordRepository {
            override val records = MutableStateFlow<List<SessionRecord>>(emptyList())
            override suspend fun save(record: SessionRecord) { saved++ }
        }, object : WeatherRepository {
            override suspend fun getTodayWeather(): DataResult<WeatherInfo> = DataResult.Unavailable("offline")
        }, FakeCalendarRepository(), audio, scope,
            detection = SensorBehaviourDetection(squat, walking, scope), clock = clock, lightCheck = light)
        fun start(vararg keys: BehaviourKey, voice: Boolean = false) {
            val modules = keys.groupBy { it.module }.map { (m, keys) -> RoutineModule(type = m,
                behaviours = keys.map { Behaviour(key = it) }, squatTarget = 2, stepTarget = 3) }
            engine.start(RoutineTemplate(id = "sensor-test", name = "Sensor test", modules = modules),
                AppSettings(audio = AudioSettings(voiceGuidanceEnabled = voice)))
        }
        val state get() = engine.state.value
    }

    @Test fun reachingSquatTargetStartsWalkingAtZeroWithoutCombinedSensors() = runTest {
        val f = Fixture(this)
        f.walking.count.value = 90
        f.start(BehaviourKey.SQUATS, BehaviourKey.WALKING, BehaviourKey.LEAVING_REMINDER); runCurrent()
        assertTrue(f.squat.running); assertFalse(f.walking.running)
        f.squat.move(1); runCurrent(); assertEquals(1, f.state.sensor.squatCount)
        f.squat.move(10); runCurrent()
        assertEquals(BehaviourKey.WALKING, f.state.currentBehaviour!!.key)
        assertFalse(f.squat.running); assertTrue(f.walking.running)
        assertEquals(0, f.state.sensor.stepCount)
        f.walking.move(3); runCurrent()
        assertEquals(BehaviourKey.LEAVING_REMINDER, f.state.currentBehaviour!!.key)
        assertFalse(f.walking.running)
        assertEquals(CompletionKind.VERIFIED, f.state.results.single().completionKind)
        assertEquals(0, f.legacyStarts)
        f.engine.skip(); runCurrent()
        assertEquals(SessionStatus.COMPLETED, f.state.status)
        assertEquals(1, f.saved)
    }

    @Test fun skipOnlyAdvancesCurrentBehaviourAndDoesNotSkipWalking() = runTest {
        val f = Fixture(this)
        f.start(BehaviourKey.STRETCHING, BehaviourKey.SQUATS, BehaviourKey.WALKING); runCurrent()
        assertFalse(f.squat.running); assertFalse(f.walking.running)
        f.engine.skip(); runCurrent(); assertEquals(BehaviourKey.SQUATS, f.state.currentBehaviour!!.key)
        f.engine.skip(); runCurrent(); assertEquals(BehaviourKey.WALKING, f.state.currentBehaviour!!.key)
        assertEquals(0, f.state.currentModuleIndex)
        f.engine.exit(); runCurrent(); assertFalse(f.walking.running)
    }

    @Test fun pauseKeepsCountsAndTimeResumeDoesNotReset() = runTest {
        val f = Fixture(this)
        f.start(BehaviourKey.WALKING); runCurrent()
        f.walking.move(1); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        f.engine.pause(); runCurrent()
        val seconds = f.state.remainingSeconds
        advanceTimeBy(20_000); runCurrent(); f.walking.move(50)
        assertEquals(seconds, f.state.remainingSeconds)
        f.engine.resume(); runCurrent()
        assertEquals(1, f.state.sensor.stepCount); assertEquals(1, f.walking.resets)
        f.walking.move(2); runCurrent()
        assertEquals(SessionStatus.COMPLETED, f.state.status)
    }

    @Test fun individualTimeLimitIsTimedNotVerifiedAndDoesNotStartOtherSensorEarly() = runTest {
        val f = Fixture(this)
        f.start(BehaviourKey.SQUATS, BehaviourKey.WALKING); runCurrent()
        val squatSeconds = f.state.remainingSeconds
        advanceTimeBy(squatSeconds * 1000L); runCurrent()
        assertEquals(BehaviourKey.WALKING, f.state.currentBehaviour!!.key)
        assertEquals(0, f.state.sensor.stepCount)
        advanceTimeBy(f.state.remainingSeconds * 1000L); runCurrent()
        assertEquals(SessionStatus.COMPLETED, f.state.status)
        assertEquals(CompletionKind.TIMED, f.state.results.single().completionKind)
    }

    @Test fun repeatedStartDoesNotResetRunningSensorAndExitedSessionRejectsLateCounts() = runTest {
        val f = Fixture(this)
        f.start(BehaviourKey.SQUATS, BehaviourKey.WALKING); runCurrent()
        f.squat.move(1); runCurrent()
        f.start(BehaviourKey.WALKING); runCurrent()
        assertEquals(BehaviourKey.SQUATS, f.state.currentBehaviour!!.key)
        assertEquals(1, f.squat.resets)
        f.engine.exit(); runCurrent()
        f.squat.count.value = 999; runCurrent()
        assertEquals(SessionStatus.EXITED, f.state.status)
        assertFalse(f.walking.running)
    }

    @Test fun brightRoomOmitsOpenLightInstructionAndDarkOrNoReadingPlaysItOnce() = runTest {
        val instruction = "Turn on the lights you need."
        for (reading in listOf<Boolean?>(null, true, false)) {
            var checks = 0
            val f = Fixture(this, SessionLightCheck { checks++; reading })
            f.start(BehaviourKey.TURN_ON_LIGHT, voice = true)
            advanceTimeBy(19_000); runCurrent()
            assertEquals(1, checks)
            assertEquals(if (reading == true) 0 else 1, f.audio.lines.count { it == instruction })
            assertFalse(f.audio.lines.contains("It's still a little dark in here."))
            assertTrue(f.audio.lines.contains("Good. On we go."))
            assertEquals(SessionStatus.RUNNING, f.state.status)
            f.engine.exit(); runCurrent()
        }
    }

    @Test fun getSomeLightAndOpenCurtainsPlayNormalGuidanceWithoutReadingLight() = runTest {
        for (key in listOf(BehaviourKey.LIGHT_REMINDER, BehaviourKey.OPEN_CURTAINS)) {
            var checks = 0
            val f = Fixture(this, SessionLightCheck { checks++; true })
            f.start(key, voice = true)
            advanceTimeBy(15_000); runCurrent()
            assertEquals(0, checks)
            assertTrue(f.audio.lines.isNotEmpty())
            if (key == BehaviourKey.LIGHT_REMINDER) {
                assertTrue(f.audio.lines.contains("Switch on a light, so your body knows it's morning."))
            } else {
                assertTrue(f.audio.lines.contains("Open the curtains wide."))
                assertFalse(f.audio.lines.contains("It's still a little dark in here."))
            }
            f.engine.exit(); runCurrent()
        }
    }

    @Test fun lightCheckStillRunsWithoutVoiceAndDoesNotAdvanceTheBehaviour() = runTest {
        var checks = 0
        val f = Fixture(this, SessionLightCheck { checks++; false })
        f.start(BehaviourKey.TURN_ON_LIGHT); runCurrent()
        assertEquals(1, checks); assertTrue(f.audio.lines.isEmpty())
        assertEquals(SessionStatus.RUNNING, f.state.status)
        f.engine.exit(); runCurrent()
    }

    @Test fun completedBrightnessCheckIsReusedOnResume() = runTest {
        var checks = 0
        val f = Fixture(this, SessionLightCheck { checks++; true })
        f.start(BehaviourKey.TURN_ON_LIGHT, voice = true); runCurrent()
        f.engine.pause(); runCurrent()
        advanceTimeBy(5_000)
        f.engine.resume(); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertEquals(1, checks)
        assertFalse(f.audio.lines.any { it.startsWith("Turn on the lights") })
        assertEquals(SessionStatus.RUNNING, f.state.status)
        f.engine.exit(); runCurrent()
    }

    @Test fun leavingWaitsForFinishAndSavesHistoryOnce() = runTest {
        val f = Fixture(this)
        f.start(BehaviourKey.LEAVING_REMINDER); runCurrent()
        advanceTimeBy(200_000); runCurrent()
        assertEquals(SessionStatus.RUNNING, f.state.status)
        assertEquals(0, f.saved)
        f.engine.skip(); runCurrent()
        assertEquals(SessionStatus.COMPLETED, f.state.status)
        assertEquals(1, f.saved)
        // Further updates after completion must not write the same morning twice.
        f.engine.skip(); f.engine.pause(); runCurrent()
        assertEquals(1, f.saved)
    }
    @Test fun unavailableSensorCannotVerifyAndStillEndsAtItsTimeLimit() = runTest {
        val f = Fixture(this)
        f.walking.available = false
        f.start(BehaviourKey.WALKING); runCurrent()
        f.walking.count.value = 999; runCurrent()
        assertEquals(0, f.state.sensor.stepCount)
        advanceTimeBy(f.state.remainingSeconds * 1000L); runCurrent()
        assertEquals(SessionStatus.COMPLETED, f.state.status)
        assertEquals(CompletionKind.TIMED, f.state.results.single().completionKind)
    }

}

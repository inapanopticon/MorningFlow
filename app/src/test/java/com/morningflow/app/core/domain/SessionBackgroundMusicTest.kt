package com.morningflow.app.core.domain

import com.morningflow.app.core.audio.*
import com.morningflow.app.core.data.*
import com.morningflow.app.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/**
 * The engine starts and stops the music; the settings toggle decides whether it may.
 * Guarding the toggle here keeps the Settings switch honest - without it the switch
 * saves a preference the session never reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionBackgroundMusicTest {
    private class MusicAudio : AudioController {
        val startVolumes = mutableListOf<Float>()
        var stops = 0
        override fun startBackgroundMusic(volume: Float) { startVolumes += volume }
        override fun stopBackgroundMusic() { stops++ }
        override suspend fun playSegment(segment: NarrationSegment, volume: Float) = Unit
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

    private fun TestScope.engine(audio: MusicAudio) = SessionEngine(
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
    )

    private fun template(key: BehaviourKey = BehaviourKey.STRETCHING) = RoutineTemplate(
        id = "music-test",
        name = "Music test",
        modules = listOf(RoutineModule(type = key.module, behaviours = listOf(Behaviour(key = key)))),
    )

    private fun settings(enabled: Boolean, volume: Float = 0.4f) = AppSettings(
        audio = AudioSettings(
            backgroundMusicEnabled = enabled,
            backgroundMusicVolume = volume,
            voiceGuidanceEnabled = false,
        ),
    )

    @Test
    fun musicStartsWithTheSessionAtTheConfiguredVolume() = runTest {
        val audio = MusicAudio()
        val engine = engine(audio)

        engine.start(template(), settings(enabled = true, volume = 0.4f)); runCurrent()

        assertEquals(listOf(0.4f), audio.startVolumes)
        engine.exit(); runCurrent()
    }

    @Test
    fun disabledMusicNeverStartsAcrossStartPauseAndResume() = runTest {
        val audio = MusicAudio()
        val engine = engine(audio)

        engine.start(template(), settings(enabled = false)); runCurrent()
        engine.pause(); runCurrent()
        engine.resume(); runCurrent()

        assertTrue(audio.startVolumes.isEmpty())
        engine.exit(); runCurrent()
    }

    @Test
    fun pauseStopsTheMusicAndResumeBringsItBack() = runTest {
        val audio = MusicAudio()
        val engine = engine(audio)
        engine.start(template(), settings(enabled = true)); runCurrent()

        engine.pause(); runCurrent()
        assertEquals(1, audio.stops)

        engine.resume(); runCurrent()
        assertEquals(2, audio.startVolumes.size)
        engine.exit(); runCurrent()
    }
}

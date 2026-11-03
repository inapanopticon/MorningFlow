package com.morningflow.app.core.domain

import com.morningflow.app.core.audio.*
import com.morningflow.app.core.data.*
import com.morningflow.app.core.data.calendar.impl.CalendarRepositoryAdapter
import com.morningflow.app.core.model.*
import com.morningflow.app.core.model.calendar.CalendarEvent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionCalendarIntegrationTest {
    private class Audio : AudioController {
        val spoken = mutableListOf<String>()
        override fun startBackgroundMusic(volume: Float) = Unit
        override fun stopBackgroundMusic() = Unit
        override suspend fun playSegment(segment: NarrationSegment, volume: Float) { spoken += segment.text }
        override suspend fun speak(text: String, volume: Float) = Unit
        override fun stopGuidance() = Unit
        override fun playCue(enabled: Boolean) = Unit
        override fun release() = Unit
    }
    private fun template() = RoutineTemplate(id = "calendar-test", name = "Calendar test",
        modules = listOf(RoutineModule(type = ModuleType.LEAVING_REMINDER,
            behaviours = listOf(Behaviour(key = BehaviourKey.LEAVING_REMINDER)))))

    private fun engine(scope: kotlinx.coroutines.CoroutineScope, audio: Audio,
        query: suspend () -> DataResult<List<CalendarEvent>>,
        weatherQuery: suspend () -> DataResult<WeatherInfo> = { DataResult.Unavailable("offline") }): SessionEngine {
        val formal = object : com.morningflow.app.core.data.calendar.api.CalendarRepository {
            override suspend fun getRemainingTodayEvents() = query()
        }
        return SessionEngine(object : SensorRepository {
            override val snapshot = MutableStateFlow(SensorSnapshot())
            override fun startDemoSignals() = Unit
            override fun pause() = Unit
            override fun stop() = Unit
            override fun reset() = Unit
        }, object : SessionRecordRepository {
            override val records = MutableStateFlow<List<SessionRecord>>(emptyList())
            override suspend fun save(record: SessionRecord) = Unit
        }, object : WeatherRepository {
            override suspend fun getTodayWeather() = weatherQuery()
        }, CalendarRepositoryAdapter(formal), audio, scope)
    }

    @Test fun successfulStartReadsFormalAdapterAndNarratesEventTitle() = runTest {
        val audio = Audio()
        val start = Instant.parse("2026-10-05T01:00:00Z")
        var queries = 0
        val session = engine(this, audio, {
            queries++
            DataResult.Success(listOf(CalendarEvent("Real contract meeting", start, start.plusSeconds(3600), false)))
        })
        session.start(template(), AppSettings())
        runCurrent()
        assertEquals(SessionStatus.RUNNING, session.state.value.status)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, queries)
        assertTrue(audio.spoken.joinToString(" ").contains("Real contract meeting"))
        session.exit()
    }

    @Test fun emptyAndFailuresRemainDistinctAfterStart() = runTest {
        for (reason in listOf<String?>(null, "PERMISSION_DENIED", "PROVIDER_UNAVAILABLE", "QUERY_FAILED")) {
            val audio = Audio()
            val session = engine(this, audio, {
                if (reason == null) DataResult.Success(emptyList()) else DataResult.Unavailable(reason)
            })
            session.start(template(), AppSettings())
            advanceTimeBy(30_000); runCurrent()
            val text = audio.spoken.joinToString(" ")
            assertTrue(text.contains(if (reason == null) "calendar is clear" else "calendar isn't available"))
            assertEquals(reason == null, text.contains("calendar is clear"))
            session.exit()
        }
    }

    @Test fun throwingOrHangingSourceDoesNotCrashOrBlockNarrationForever() = runTest {
        for (hang in listOf(false, true)) {
            val audio = Audio()
            val session = engine(this, audio, {
                if (hang) awaitCancellation() else error("provider died")
            }, weatherQuery = { awaitCancellation() })
            session.start(template(), AppSettings())
            advanceTimeBy(30_000); runCurrent()
            assertTrue(audio.spoken.joinToString(" ").contains("calendar isn't available"))
            assertEquals(SessionStatus.RUNNING, session.state.value.status)
            session.exit()
        }
    }

    @Test fun exitCancelsPendingQueryAndDoesNotPlayLateCalendarAudio() = runTest {
        val audio = Audio()
        var cancelled = false
        val session = engine(this, audio, {
            try { awaitCancellation() } finally { cancelled = true }
        })
        session.start(template(), AppSettings()); runCurrent()
        session.exit(); runCurrent()
        assertTrue(cancelled)
        advanceTimeBy(10_000); runCurrent()
        assertTrue(audio.spoken.isEmpty())
        assertEquals(SessionStatus.EXITED, session.state.value.status)
    }
}

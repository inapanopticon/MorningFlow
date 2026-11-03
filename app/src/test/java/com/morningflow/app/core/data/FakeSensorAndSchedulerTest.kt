package com.morningflow.app.core.data

import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.GlobalMorningSchedule
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSensorRepositoryTest {
    private val start = Instant.parse("2026-09-10T07:05:00Z")
    private val clock = MutableClock(start)
    private val repository = FakeSensorRepository(clock, Duration.ofMinutes(2))

    @Test
    fun reportingAWakeUpStampsTheDetectionTime() {
        repository.reportWakeUp()

        val snapshot = repository.snapshot.value
        assertTrue(snapshot.wakeUpDetected)
        assertEquals(start, snapshot.wakeUpDetectedAt)
    }

    @Test
    fun aRepeatInsideTheIntervalLeavesTheSnapshotUntouched() {
        repository.reportWakeUp()
        val afterFirst = repository.snapshot.value

        clock.advance(Duration.ofSeconds(30))
        repository.reportWakeUp()

        assertEquals(afterFirst, repository.snapshot.value)
    }

    @Test
    fun aRepeatAfterTheIntervalPublishesANewSnapshot() {
        repository.reportWakeUp()
        val afterFirst = repository.snapshot.value

        clock.advance(Duration.ofMinutes(2))
        repository.reportWakeUp()

        val afterSecond = repository.snapshot.value
        assertEquals(false, afterFirst == afterSecond)
        assertEquals(start.plus(Duration.ofMinutes(2)), afterSecond.wakeUpDetectedAt)
    }

    @Test
    fun resetClearsTheDetectionAndItsTimestamp() {
        repository.reportWakeUp()
        repository.reset()

        val snapshot = repository.snapshot.value
        assertFalse(snapshot.wakeUpDetected)
        assertNull(snapshot.wakeUpDetectedAt)

        // A detection straight after a reset counts, rather than being gated as a repeat.
        repository.reportWakeUp()
        assertNotNull(repository.snapshot.value.wakeUpDetectedAt)
    }
}

class FakeWakeupSchedulerTest {
    private val scheduler = FakeWakeupScheduler()

    private val schedule = GlobalMorningSchedule(
        earliestWakeUp = LocalTime.of(7, 0),
        latestWakeUp = LocalTime.of(7, 30),
        readyBy = LocalTime.of(9, 0)
    )

    @Test
    fun theScheduleIsArmedAsASingleDailyWindow() = runBlocking {
        scheduler.schedule(AppSettings(schedule = schedule))

        assertEquals(WakeupScheduleStatus.Scheduled, scheduler.status.value)
        assertEquals(schedule, scheduler.scheduledFor.value)
    }

    @Test
    fun anOutOfOrderScheduleIsNotArmed() = runBlocking {
        val reversed = schedule.copy(latestWakeUp = LocalTime.of(6, 0))

        scheduler.schedule(AppSettings(schedule = reversed))

        assertEquals(
            WakeupScheduleStatus.Failed(WakeupScheduleFailureReason.END_BEFORE_START),
            scheduler.status.value,
        )
        assertNull(scheduler.scheduledFor.value)
    }

    @Test
    fun rearmingReplacesThePreviousSchedule() = runBlocking {
        scheduler.schedule(AppSettings(schedule = schedule))
        val later = schedule.copy(
            earliestWakeUp = LocalTime.of(8, 0),
            latestWakeUp = LocalTime.of(8, 30)
        )

        scheduler.schedule(AppSettings(schedule = later))

        assertEquals(later, scheduler.scheduledFor.value)
    }

    @Test
    fun cancelDisarmsTheSchedule() = runBlocking {
        scheduler.schedule(AppSettings(schedule = schedule))

        scheduler.cancel()

        assertEquals(WakeupScheduleStatus.NotScheduled, scheduler.status.value)
        assertNull(scheduler.scheduledFor.value)
    }
}

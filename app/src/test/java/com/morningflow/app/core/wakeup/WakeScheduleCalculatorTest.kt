package com.morningflow.app.core.wakeup

import com.morningflow.app.core.model.GlobalMorningSchedule
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeScheduleCalculatorTest {
    private val utc = ZoneOffset.UTC
    private val schedule = GlobalMorningSchedule(
        earliestWakeUp = LocalTime.of(7, 0),
        latestWakeUp = LocalTime.of(7, 30),
    )

    @Test
    fun beforeWindowSchedulesToday() {
        val calculator = calculatorAt("2026-10-02T06:59:59Z")

        val result = calculator.calculate(schedule).valid()

        assertEquals(WakeWindowPosition.BEFORE, result.position)
        assertEquals("2026-10-02T07:00Z", result.nextStartAt.toString())
    }

    @Test
    fun earliestIsInsideAndSchedulesTomorrow() {
        val calculator = calculatorAt("2026-10-02T07:00:00Z")

        val result = calculator.calculate(schedule).valid()

        assertEquals(WakeWindowPosition.INSIDE, result.position)
        assertEquals("2026-10-03T07:00Z", result.nextStartAt.toString())
    }

    @Test
    fun latestIsExclusiveAndSchedulesTomorrow() {
        val calculator = calculatorAt("2026-10-02T07:30:00Z")

        val result = calculator.calculate(schedule).valid()

        assertEquals(WakeWindowPosition.AFTER, result.position)
        assertEquals("2026-10-03T07:00Z", result.nextStartAt.toString())
    }

    @Test
    fun timeAfterWindowMovesToTheNextLocalDate() {
        val calculator = calculatorAt("2026-10-02T23:00:00Z")

        val result = calculator.calculate(schedule).valid()

        assertEquals(WakeWindowPosition.AFTER, result.position)
        assertEquals("2026-10-03T07:00Z", result.nextStartAt.toString())
    }

    @Test
    fun zeroLengthWindowIsRejected() {
        val zeroLength = schedule.copy(latestWakeUp = schedule.earliestWakeUp)

        val result = calculatorAt("2026-10-02T06:00:00Z").calculate(zeroLength)

        assertEquals(
            WakeScheduleCalculation.Invalid(InvalidWakeScheduleReason.ZERO_LENGTH_WINDOW),
            result,
        )
    }

    @Test
    fun reversedWindowIsRejected() {
        val reversed = schedule.copy(
            earliestWakeUp = LocalTime.of(23, 30),
            latestWakeUp = LocalTime.of(0, 30),
        )

        val result = calculatorAt("2026-10-02T22:00:00Z").calculate(reversed)

        assertEquals(
            WakeScheduleCalculation.Invalid(InvalidWakeScheduleReason.END_BEFORE_START),
            result,
        )
    }

    @Test
    fun daylightSavingTransitionUsesTheLocalZoneInsteadOfAdding24Hours() {
        val melbourne = ZoneId.of("Australia/Melbourne")
        val scheduleAcrossTheTransition = schedule.copy(
            earliestWakeUp = LocalTime.of(6, 0),
            latestWakeUp = LocalTime.of(6, 30),
        )
        val calculator = WakeScheduleCalculator(
            clock = Clock.fixed(Instant.parse("2026-10-03T20:00:00Z"), melbourne),
            zoneId = melbourne,
        )

        val result = calculator.calculate(scheduleAcrossTheTransition).valid()

        assertEquals(WakeWindowPosition.AFTER, result.position)
        assertEquals("2026-10-05T06:00+11:00[Australia/Melbourne]", result.nextStartAt.toString())
    }

    @Test
    fun explicitZoneConvertsTheClockInstantBeforeComparingLocalTime() {
        val newYork = ZoneId.of("America/New_York")
        val calculator = WakeScheduleCalculator(
            clock = Clock.fixed(Instant.parse("2026-10-02T11:00:00Z"), ZoneOffset.UTC),
            zoneId = newYork,
        )

        val result = calculator.calculate(schedule).valid()

        assertEquals(WakeWindowPosition.INSIDE, result.position)
        assertEquals("2026-10-03T07:00-04:00[America/New_York]", result.nextStartAt.toString())
    }

    @Test
    fun eachCalculationReadsTheCurrentZoneFromTheProvider() {
        var currentZone: ZoneId = ZoneOffset.UTC
        val calculator = WakeScheduleCalculator(
            clock = Clock.fixed(Instant.parse("2026-10-02T10:30:00Z"), ZoneOffset.UTC),
            zoneIdProvider = { currentZone },
        )

        val utcResult = calculator.calculate(schedule).valid()
        currentZone = ZoneId.of("America/New_York")
        val newYorkResult = calculator.calculate(schedule).valid()

        assertEquals(WakeWindowPosition.AFTER, utcResult.position)
        assertEquals("2026-10-03T07:00Z", utcResult.nextStartAt.toString())
        assertEquals(WakeWindowPosition.BEFORE, newYorkResult.position)
        assertEquals(
            "2026-10-02T07:00-04:00[America/New_York]",
            newYorkResult.nextStartAt.toString(),
        )
    }

    private fun calculatorAt(instant: String): WakeScheduleCalculator =
        WakeScheduleCalculator(
            clock = Clock.fixed(Instant.parse(instant), utc),
            zoneId = utc,
        )

    private fun WakeScheduleCalculation.valid(): WakeScheduleCalculation.Valid {
        assertTrue("Expected a valid calculation, got $this", this is WakeScheduleCalculation.Valid)
        return this as WakeScheduleCalculation.Valid
    }

}

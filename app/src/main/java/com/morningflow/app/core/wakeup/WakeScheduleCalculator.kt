package com.morningflow.app.core.wakeup

import com.morningflow.app.core.model.GlobalMorningSchedule
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

/** The position of the current local time relative to a valid wake-up window. */
enum class WakeWindowPosition {
    BEFORE,
    INSIDE,
    AFTER,
}

/** Reasons why the Scheduler Core refuses a wake-up window. */
enum class InvalidWakeScheduleReason {
    ZERO_LENGTH_WINDOW,
    END_BEFORE_START,
}

/** The result of calculating the next daily START Alarm. */
sealed interface WakeScheduleCalculation {
    /** A valid window and the next local Earliest converted to a zoned date-time. */
    data class Valid(
        val position: WakeWindowPosition,
        val nextStartAt: ZonedDateTime,
    ) : WakeScheduleCalculation

    /** An invalid or unsupported window that must not be scheduled. */
    data class Invalid(
        val reason: InvalidWakeScheduleReason,
    ) : WakeScheduleCalculation
}

/**
 * Calculates the next daily wake-up START without depending on Android APIs.
 *
 * The shared model and Scheduler both use [Earliest, Latest), require Earliest to be strictly
 * before Latest, and do not support a window that crosses midnight. The Calculator still
 * validates those rules at the scheduling boundary rather than assuming every caller supplied
 * a previously validated model.
 */
class WakeScheduleCalculator(
    private val clock: Clock = Clock.systemUTC(),
    private val zoneIdProvider: () -> ZoneId = ZoneId::systemDefault,
) {

    /** Keeps explicit fixed-zone injection convenient for deterministic tests. */
    constructor(clock: Clock, zoneId: ZoneId) : this(clock, { zoneId })

    /**
     * Returns the current window position and the next local Earliest.
     *
     * If the current time is inside or after today's window, the next START is tomorrow.
     * This prevents an Alarm fired at Earliest from immediately scheduling itself again for
     * the same day.
     */
    fun calculate(schedule: GlobalMorningSchedule): WakeScheduleCalculation {
        val zoneId = zoneIdProvider()
        val earliest = schedule.earliestWakeUp
        val latest = schedule.latestWakeUp

        val invalidReason = when {
            earliest == latest -> InvalidWakeScheduleReason.ZERO_LENGTH_WINDOW
            latest.isBefore(earliest) -> InvalidWakeScheduleReason.END_BEFORE_START
            else -> null
        }
        if (invalidReason != null) {
            return WakeScheduleCalculation.Invalid(invalidReason)
        }

        val now = clock.instant().atZone(zoneId)
        val position = when {
            now.toLocalTime().isBefore(earliest) -> WakeWindowPosition.BEFORE
            now.toLocalTime().isBefore(latest) -> WakeWindowPosition.INSIDE
            else -> WakeWindowPosition.AFTER
        }
        val nextDate = when (position) {
            WakeWindowPosition.BEFORE -> now.toLocalDate()
            WakeWindowPosition.INSIDE,
            WakeWindowPosition.AFTER,
            -> now.toLocalDate().plusDays(1)
        }

        return WakeScheduleCalculation.Valid(
            position = position,
            nextStartAt = nextDate.atTime(earliest).atZone(zoneId),
        )
    }
}

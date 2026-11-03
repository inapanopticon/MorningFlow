package com.morningflow.app.core.data

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Decides when a wake-up signal counts as a *new* detection instead of the same one
 * still ongoing.
 *
 * Sensor algorithms fire continuously while the user moves, so without this a snapshot
 * would restamp `wakeUpDetectedAt` on every sample and the timestamp would degrade into
 * a heartbeat. The gate keeps the field meaning "a distinct wake-up happened at T" by
 * ignoring signals that arrive within [minimumRepeatInterval] of the accepted one.
 *
 * Every [SensorRepository] implementation shares this rule so the placeholder and the
 * real sensor cannot drift apart on what the rest of the app observes.
 */
class WakeUpDetectionGate(
    private val minimumRepeatInterval: Duration = DEFAULT_MINIMUM_REPEAT_INTERVAL,
    private val clock: Clock = Clock.systemUTC(),
) {
    private var acceptedAt: Instant? = null

    /** The currently published detection time, or null when nothing has been accepted. */
    val lastAcceptedAt: Instant? get() = acceptedAt

    /**
     * Offers a raw wake-up signal to the gate.
     *
     * @return the instant to publish when this is a new detection, or null when it is a
     *   repeat of the one already published and the snapshot should stay untouched.
     */
    fun offer(): Instant? {
        val now = clock.instant()
        val previous = acceptedAt
        if (previous != null && Duration.between(previous, now) < minimumRepeatInterval) {
            return null
        }
        acceptedAt = now
        return now
    }

    /** Forgets the accepted detection, so the next signal starts a fresh sequence. */
    fun reset() {
        acceptedAt = null
    }

    companion object {
        /**
         * Matches the product rule "remind again if the user still hasn't started after
         * two minutes", so a held-down signal cannot produce reminders faster than the
         * user is expected to act on them.
         */
        val DEFAULT_MINIMUM_REPEAT_INTERVAL: Duration = Duration.ofMinutes(2)
    }
}

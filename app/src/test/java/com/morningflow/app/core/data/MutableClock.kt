package com.morningflow.app.core.data

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test moves by hand, so no test has to sleep to cross a time threshold. */
class MutableClock(private var now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this

    fun advance(amount: Duration) {
        now = now.plus(amount)
    }
}

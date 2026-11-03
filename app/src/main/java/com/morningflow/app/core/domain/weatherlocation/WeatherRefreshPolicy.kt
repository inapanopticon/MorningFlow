package com.morningflow.app.core.domain.weatherlocation

/** Defines the freshness window used by manual and automatic weather refreshes. */
data class WeatherRefreshPolicy(
    val intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
) {
    init {
        require(intervalMinutes in MIN_INTERVAL_MINUTES..MAX_INTERVAL_MINUTES) {
            "intervalMinutes must be between $MIN_INTERVAL_MINUTES and $MAX_INTERVAL_MINUTES"
        }
    }

    val intervalMillis: Long
        get() = intervalMinutes * MILLIS_PER_MINUTE

    fun isStale(fetchedAtEpochMs: Long, nowEpochMs: Long): Boolean =
        nowEpochMs - fetchedAtEpochMs >= intervalMillis

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 15L
        const val MIN_INTERVAL_MINUTES = 5L
        const val MAX_INTERVAL_MINUTES = 360L
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}

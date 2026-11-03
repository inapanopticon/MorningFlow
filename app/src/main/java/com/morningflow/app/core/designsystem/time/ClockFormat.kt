package com.morningflow.app.core.designsystem.time

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The single clock format for every time the app shows, following the phone's
 * 12/24-hour setting. [MainActivity][com.morningflow.app.app.MainActivity] refreshes
 * [is24Hour] whenever it comes to the foreground; tests see the 24-hour default.
 */
object ClockFormat {
    @Volatile
    var is24Hour: Boolean = true

    private val TwentyFourHour = DateTimeFormatter.ofPattern("HH:mm")
    private val TwelveHour = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun format(time: LocalTime): String = time.format(if (is24Hour) TwentyFourHour else TwelveHour)
}

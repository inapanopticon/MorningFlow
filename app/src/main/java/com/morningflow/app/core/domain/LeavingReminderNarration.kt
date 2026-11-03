package com.morningflow.app.core.domain

import com.morningflow.app.core.audio.NarrationScripts
import com.morningflow.app.core.audio.NarrationSegment
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.WeatherInfo
import com.morningflow.app.core.model.calendar.CalendarEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Puts the leaving reminder together for today.
 *
 * Every line that reads the same each day - the opening, the umbrella, jacket and
 * sunscreen nudges, the lead-in to the plans and the goodbye - is a recording in the
 * routine's own voice. Only what changes daily (the temperature and city, the events,
 * the packing list) is spoken by text-to-speech, so the device voice is limited to short
 * factual lines. A part whose data is unavailable is left out rather than read with a gap.
 */
object LeavingReminderNarration {
    private val EVENT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    /**
     * Sunscreen is a plain on/off in settings, so unlike rain and cold its cutoff is
     * still the app's to choose.
     */
    private const val HIGH_UV_REMINDER_THRESHOLD = 6

    private const val BETWEEN_PARTS_MILLIS = 1_000L
    private const val INTO_DETAILS_MILLIS = 400L

    /** Time to actually look through bag and pockets after hearing the list. */
    private const val CHECK_THINGS_MILLIS = 6_000L

    fun build(
        settings: AppSettings,
        weather: WeatherInfo?,
        events: DataResult<List<CalendarEvent>>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<NarrationSegment> = buildList {
        add(NarrationScripts.LEAVING_OPENING.copy(pauseAfterMillis = BETWEEN_PARTS_MILLIS))
        if (weather != null) {
            add(spoken("It's ${weather.temperatureCelsius} degrees in ${weather.city} today, " +
                "with ${weather.condition.lowercase()}.", BETWEEN_PARTS_MILLIS))
            weatherNudges(settings, weather).forEach { add(it.copy(pauseAfterMillis = BETWEEN_PARTS_MILLIS)) }
        }
        when (events) {
            is DataResult.Unavailable -> add(spoken("Your calendar isn't available right now.", BETWEEN_PARTS_MILLIS))
            is DataResult.Success -> if (events.value.isEmpty()) {
                add(NarrationScripts.LEAVING_CALENDAR_CLEAR.copy(pauseAfterMillis = BETWEEN_PARTS_MILLIS))
            } else {
                add(NarrationScripts.LEAVING_SCHEDULE_INTRO.copy(pauseAfterMillis = INTO_DETAILS_MILLIS))
                add(spoken(scheduleLine(events.value, zoneId), BETWEEN_PARTS_MILLIS))
            }
        }
        if (settings.thingsToBring.isNotEmpty()) {
            add(spoken("Make sure you've got your ${spokenList(settings.thingsToBring)}.", CHECK_THINGS_MILLIS))
        }
        add(NarrationScripts.LEAVING_CLOSING)
    }

    private fun weatherNudges(settings: AppSettings, weather: WeatherInfo): List<NarrationSegment> = buildList {
        val rainCutoff = settings.reminders.umbrellaRainPercent
        if (rainCutoff != null && weather.rainProbability >= rainCutoff) add(NarrationScripts.LEAVING_UMBRELLA)
        val coldCutoff = settings.reminders.jacketTemperatureCelsius
        if (coldCutoff != null && weather.temperatureCelsius <= coldCutoff) add(NarrationScripts.LEAVING_JACKET)
        if (settings.reminders.sunscreen && weather.uvIndex >= HIGH_UV_REMINDER_THRESHOLD) {
            add(NarrationScripts.LEAVING_SUNSCREEN)
        }
    }

    /** The next two events at most: enough to plan the morning around, short enough to remember. */
    private fun scheduleLine(events: List<CalendarEvent>, zoneId: ZoneId): String {
        val first = eventDescription(events.first(), zoneId)
        val second = events.getOrNull(1)?.let { eventDescription(it, zoneId) }
        return if (second == null) "$first." else "$first, then $second."
    }

    private fun eventDescription(event: CalendarEvent, zoneId: ZoneId): String =
        if (event.isAllDay) "${event.title}, all day"
        else "${event.title} at ${event.startsAt.atZone(zoneId).format(EVENT_TIME_FORMAT)}"

    private fun spokenList(items: List<String>): String =
        if (items.size == 1) items.single()
        else items.dropLast(1).joinToString(", ") + " and " + items.last()

    private fun spoken(text: String, pauseAfterMillis: Long) =
        NarrationSegment(NarrationSegment.NO_CLIP, text, pauseAfterMillis)
}

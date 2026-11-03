package com.morningflow.app.core.domain

import com.morningflow.app.core.audio.NarrationScripts
import com.morningflow.app.core.audio.NarrationSegment
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.ReminderPreferences
import com.morningflow.app.core.model.WeatherInfo
import com.morningflow.app.core.model.calendar.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LeavingReminderNarrationTest {
    private fun event(title: String, time: LocalTime): CalendarEvent {
        val start = LocalDate.of(2026, 10, 3).atTime(time).atZone(ZoneId.of("Australia/Melbourne")).toInstant()
        return CalendarEvent(title, start, start.plusSeconds(3600), false)
    }

    private val wetAndCold = WeatherInfo(
        city = "Melbourne",
        condition = "Light rain",
        temperatureCelsius = 8,
        rainProbability = 80,
        uvIndex = 7,
    )

    private fun spoken(
        settings: AppSettings = AppSettings(),
        weather: WeatherInfo? = wetAndCold,
        events: List<CalendarEvent> = emptyList(),
    ) = LeavingReminderNarration.build(settings, weather, DataResult.Success(events), ZoneId.of("Australia/Melbourne")).joinToString(" ") { it.text }

    /** The device voice is kept to the day's own details; everything else is the recorded voice. */
    @Test
    fun onlyTheDaysDetailsAreLeftToTextToSpeech() {
        val segments = LeavingReminderNarration.build(
            AppSettings(),
            wetAndCold,
            DataResult.Success(listOf(event("Standup", LocalTime.of(9, 0)))),
            ZoneId.of("Australia/Melbourne"),
        )
        assertEquals(NarrationScripts.LEAVING_OPENING.clipRes, segments.first().clipRes)
        assertEquals(NarrationScripts.LEAVING_CLOSING.clipRes, segments.last().clipRes)
        val spokenByDevice = segments.filter { it.clipRes == NarrationSegment.NO_CLIP }.map { it.text }
        assertEquals(3, spokenByDevice.size)
        assertTrue(spokenByDevice[0].contains("8 degrees in Melbourne"))
        assertTrue(spokenByDevice[1].contains("Standup"))
        assertTrue(spokenByDevice[2].startsWith("Make sure you've got your"))
    }

    @Test
    fun weatherLineNamesTheTemperatureAndCity() {
        val text = spoken()
        assertTrue(text.contains("8 degrees"))
        assertTrue(text.contains("Melbourne"))
    }

    @Test
    fun remindersFollowTheUsersToggles() {
        val allOff = AppSettings(
            reminders = ReminderPreferences(
                umbrellaRainPercent = null,
                jacketTemperatureCelsius = null,
                sunscreen = false,
            ),
        )
        val text = spoken(settings = allOff)
        assertFalse(text.contains("umbrella"))
        assertFalse(text.contains("jacket"))
        assertFalse(text.contains("sunscreen"))
    }

    /**
     * The cutoff is the user's now, so the same weather has to produce a different
     * reminder depending on how insistent they asked each one to be.
     */
    @Test
    fun theUsersChosenCutoffDecidesWhetherAReminderFires() {
        // 50% rain, 9 degrees.
        val weather = wetAndCold.copy(rainProbability = 50, temperatureCelsius = 9)

        val sensitive = AppSettings(
            reminders = ReminderPreferences(
                umbrellaRainPercent = 40,
                jacketTemperatureCelsius = 10,
                sunscreen = false,
            ),
        )
        val sensitiveText = spoken(settings = sensitive, weather = weather)
        assertTrue(sensitiveText.contains("umbrella"))
        assertTrue(sensitiveText.contains("jacket"))

        val relaxed = AppSettings(
            reminders = ReminderPreferences(
                umbrellaRainPercent = 60,
                jacketTemperatureCelsius = 5,
                sunscreen = false,
            ),
        )
        val relaxedText = spoken(settings = relaxed, weather = weather)
        assertFalse(relaxedText.contains("umbrella"))
        assertFalse(relaxedText.contains("jacket"))
    }

    /** The cutoff is inclusive: exactly at the chosen number still reminds. */
    @Test
    fun aCutoffIsInclusive() {
        val settings = AppSettings(
            reminders = ReminderPreferences(
                umbrellaRainPercent = 40,
                jacketTemperatureCelsius = 8,
                sunscreen = false,
            ),
        )
        val text = spoken(
            settings = settings,
            weather = wetAndCold.copy(rainProbability = 40, temperatureCelsius = 8),
        )
        assertTrue(text.contains("umbrella"))
        assertTrue(text.contains("jacket"))
    }

    @Test
    fun remindersOnlyFireWhenTheWeatherWarrantsThem() {
        val mild = wetAndCold.copy(rainProbability = 5, temperatureCelsius = 24, uvIndex = 1)
        val text = spoken(weather = mild)
        assertFalse(text.contains("umbrella"))
        assertFalse(text.contains("jacket"))
        assertFalse(text.contains("sunscreen"))
    }

    /** A line with nothing to say is dropped outright rather than read with a hole in it. */
    @Test
    fun emptyPackingListDropsItsLine() {
        val text = spoken(settings = AppSettings(thingsToBring = emptyList()))
        assertFalse(text.contains("Make sure you've got"))
    }

    @Test
    fun unavailableWeatherDropsItsLineButKeepsTheRest() {
        val segments = LeavingReminderNarration.build(AppSettings(), weather = null, events = DataResult.Success(emptyList()))
        val text = segments.joinToString(" ") { it.text }
        assertFalse(text.contains("degrees"))
        assertTrue(text.contains("Have a wonderful day"))
    }

    @Test
    fun scheduleLineReadsTheFirstTwoEvents() {
        val text = spoken(
            events = listOf(
                event("Project meeting", LocalTime.of(9, 0)),
                event("Team check-in", LocalTime.of(14, 30)),
                event("Gym", LocalTime.of(18, 0)),
            ),
        )
        assertTrue(text.contains("Project meeting"))
        assertTrue(text.contains("Team check-in"))
        assertFalse(text.contains("Gym"))
    }

    @Test
    fun emptyCalendarStillSaysSomething() {
        assertTrue(spoken(events = emptyList()).contains("calendar is clear"))
    }

    @Test
    fun aSingleEventIsReadOnItsOwn() {
        val text = spoken(events = listOf(event("Standup", LocalTime.of(9, 0))))
        assertTrue(Regex("Standup at 9:00.{1,3}m\\.", RegexOption.IGNORE_CASE).containsMatchIn(text))
        assertFalse(text.contains(", then"))
    }

    @Test
    fun thePackingListReadsLikeASentence() {
        val text = spoken(settings = AppSettings(thingsToBring = listOf("Keys", "Phone", "Wallet")))
        assertTrue(text.contains("Make sure you've got your Keys, Phone and Wallet."))
    }

    @Test
    fun droppingALineDropsItsPauseToo() {
        val full = LeavingReminderNarration.build(
            AppSettings(),
            wetAndCold,
            DataResult.Success(listOf(event("Standup", LocalTime.of(9, 0)))),
        )
        val trimmed = LeavingReminderNarration.build(
            AppSettings(thingsToBring = emptyList()),
            weather = null,
            events = DataResult.Success(emptyList()),
        )
        assertTrue(trimmed.size < full.size)
        assertTrue(trimmed.sumOf { it.pauseAfterMillis } < full.sumOf { it.pauseAfterMillis })
    }
    @Test fun failuresAreNotNarratedAsAnEmptySchedule() {
        for (reason in listOf("PERMISSION_DENIED", "PROVIDER_UNAVAILABLE", "QUERY_FAILED")) {
            val text = LeavingReminderNarration.build(AppSettings(), null, DataResult.Unavailable(reason))
                .joinToString(" ") { it.text }
            assertTrue(text.contains("calendar isn't available"))
            assertFalse(text.contains("calendar is clear"))
        }
    }

    @Test fun allDayEventDoesNotReadMidnightAndRepositoryOrderIsPreserved() {
        val allDay = event("Holiday", LocalTime.MIDNIGHT).copy(isAllDay = true)
        val text = spoken(events = listOf(allDay, event("Meeting", LocalTime.of(9, 0))))
        assertTrue(text.contains("Holiday, all day"))
        assertFalse(text.contains("12:00"))
        assertTrue(text.indexOf("Holiday") < text.indexOf("Meeting"))
        assertTrue(text.contains("9:00"))
    }

}

package com.morningflow.app.feature.templates

import com.morningflow.app.core.designsystem.time.ClockFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TemplateTimeTextTest {

    @After
    fun restoreClock() {
        ClockFormat.is24Hour = true
    }

    @Test
    fun editorTimeSurvivesATwelveHourPhone() {
        ClockFormat.is24Hour = false
        val time = LocalTime.of(19, 5)

        assertEquals(time, parseTemplateTime(time.toTemplateInputText()))
        assertEquals("7:05 PM", time.toTemplateTimeText())
    }
}

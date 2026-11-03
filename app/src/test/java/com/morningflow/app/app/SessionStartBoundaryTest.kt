package com.morningflow.app.app

import com.morningflow.app.core.data.defaultTemplateSeed
import com.morningflow.app.core.domain.runtime.StartResult
import org.junit.Assert.*
import org.junit.Test

class SessionStartBoundaryTest {
    private class Cleanup {
        var detectionStops = 0
        var promptCancels = 0
        val order = mutableListOf<String>()
        fun run() {
            detectionStops++
            order += "stop"
            promptCancels++
            order += "cancel"
        }
        fun assertCounts(expected: Int) {
            assertEquals(expected, detectionStops)
            assertEquals(expected, promptCancels)
        }
    }
    @Test fun successfulStartUsesExactIdAndPerformsBothCleanupActionsOnce() {
        val templates = defaultTemplateSeed()
        val requested = templates.last()
        val cleanup = Cleanup()
        assertTrue(startSelectedSession(templates, requested.id, {
            assertEquals(requested, it)
            StartResult.STARTED
        }, cleanup::run))
        cleanup.assertCounts(1)
        assertEquals(listOf("stop", "cancel"), cleanup.order)
    }
    @Test fun missingTemplateNeverStartsOrCleansUp() {
        val cleanup = Cleanup()
        assertFalse(startSelectedSession(defaultTemplateSeed(), "missing", {
            error("must not start fallback")
        }, cleanup::run))
        cleanup.assertCounts(0)
    }
    @Test fun everyRejectedResultKeepsPromptAndDetectionUntouched() {
        val templates = defaultTemplateSeed()
        for (result in StartResult.entries.filter { it != StartResult.STARTED }) {
            val cleanup = Cleanup()
            assertFalse(startSelectedSession(templates, templates.first().id, { result }, cleanup::run))
            cleanup.assertCounts(0)
        }
    }
    @Test fun repeatedStartDoesNotRepeatEitherCleanupAction() {
        val templates = defaultTemplateSeed()
        var active = false
        val cleanup = Cleanup()
        val start: (com.morningflow.app.core.model.RoutineTemplate) -> StartResult = {
            if (active) StartResult.ALREADY_ACTIVE else { active = true; StartResult.STARTED }
        }
        assertTrue(startSelectedSession(templates, templates.first().id, start, cleanup::run))
        assertFalse(startSelectedSession(templates, templates.first().id, start, cleanup::run))
        cleanup.assertCounts(1)
    }
    @Test fun throwingStartDoesNotCleanUp() {
        val cleanup = Cleanup()
        val templates = defaultTemplateSeed()
        assertThrows(IllegalStateException::class.java) {
            startSelectedSession(templates, templates.first().id, { error("start failed") }, cleanup::run)
        }
        cleanup.assertCounts(0)
    }
    @Test fun noActiveDetectionDoesNotUndoSuccessfulStartOrSkipPromptCancellation() {
        val templates = defaultTemplateSeed()
        var promptCancels = 0
        val stop: () -> Boolean = { false }
        assertTrue(startSelectedSession(templates, templates.first().id, { StartResult.STARTED }, {
            stop() // Return value intentionally ignored at the successful boundary.
            promptCancels++
        }))
        assertEquals(1, promptCancels)
    }
}

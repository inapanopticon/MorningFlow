package com.morningflow.app.app

import com.morningflow.app.core.data.testTemplateSeed
import com.morningflow.app.core.model.RoutineTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MorningFlowViewModelTest {
    @Test
    fun explicitTemplateIdSelectsThatTemplateEvenWhenAnotherHasHigherPriority() {
        val requested = testTemplateSeed().first { it.id == "dev-later-morning" }
        val selected = selectTemplateById(testTemplateSeed(), requested.id)

        assertEquals(requested.id, selected?.id)
        assertEquals(requested.priority, selected?.priority)
    }

    @Test
    fun explicitTemplateIdDoesNotFallBackToAnotherTemplate() {
        assertNull(selectTemplateById(testTemplateSeed(), "missing-template"))
    }

    @Test
    fun explicitTemplateIdIsIndependentOfListOrder() {
        val templates = testTemplateSeed().sortedByDescending(RoutineTemplate::priority)
        val requested = templates.last()

        assertEquals(requested.id, selectTemplateById(templates, requested.id)?.id)
    }
}

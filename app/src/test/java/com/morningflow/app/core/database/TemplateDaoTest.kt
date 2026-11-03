package com.morningflow.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.model.Behaviour
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.ModuleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TemplateDaoTest {
    private lateinit var database: MorningFlowDatabase
    private lateinit var dao: TemplateDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MorningFlowDatabase::class.java
        ).build()
        dao = database.templateDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun template(
        id: String,
        priority: Int = 1,
        isDefault: Boolean = false
    ) = TemplateEntity(
        id = id,
        name = "Template $id",
        applicableDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
        applicableStart = LocalTime.of(5, 0),
        applicableEnd = LocalTime.of(11, 0),
        priority = priority,
        isDefault = isDefault,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun module(id: String, templateId: String, position: Int) = ModuleEntity(
        id = id,
        templateId = templateId,
        type = ModuleType.LEAVE_BED,
        enabled = true,
        behaviours = listOf(
            Behaviour(key = BehaviourKey.LEAVE_BED, locked = true),
            Behaviour(key = BehaviourKey.CHANGE_INTO_HOME_CLOTHES, locked = true)
        ),
        squatTarget = 10,
        stepTarget = 40,
        position = position
    )

    @Test
    fun saveTemplateWithModules_insertsBothAndPreservesOrder() = runTest {
        dao.saveTemplateWithModules(
            template("t1"),
            listOf(module("m2", "t1", 1), module("m1", "t1", 0))
        )

        val rows = dao.observeTemplatesWithModules().first()
        assertEquals(1, rows.size)
        val modules = rows.first().modules.sortedBy { it.position }
        assertEquals(listOf("m1", "m2"), modules.map { it.id })
    }

    @Test
    fun saveTemplateWithModules_secondSaveReplacesModulesEntirely() = runTest {
        dao.saveTemplateWithModules(template("t1"), listOf(module("m1", "t1", 0), module("m2", "t1", 1)))

        // Re-saving with a single, different module must drop the old ones, not merge them.
        dao.saveTemplateWithModules(template("t1"), listOf(module("m3", "t1", 0)))

        val rows = dao.observeTemplatesWithModules().first()
        val modules = rows.single().modules
        assertEquals(listOf("m3"), modules.map { it.id })
    }

    @Test
    fun deleteTemplate_removesModulesViaCascade() = runTest {
        dao.saveTemplateWithModules(template("t1"), listOf(module("m1", "t1", 0)))

        dao.deleteTemplate("t1")

        assertEquals(0, dao.countTemplates())
        assertEquals(0, dao.observeTemplatesWithModules().first().size)
    }

    @Test
    fun deleteTemplate_removesTemplate() = runTest {
        dao.saveTemplateWithModules(template("template"), emptyList())

        dao.deleteTemplate("template")

        assertEquals(0, dao.countTemplates())
        assertNull(dao.getTemplate("template"))
    }

    @Test
    fun setDefault_onlyOneTemplateCanBeDefaultAtATime() = runTest {
        dao.upsertTemplate(template("a"))
        dao.upsertTemplate(template("b"))

        dao.setDefault("a")
        assertTrue(dao.getTemplate("a")!!.isDefault)
        assertFalse(dao.getTemplate("b")!!.isDefault)

        dao.setDefault("missing")
        assertTrue(dao.getTemplate("a")!!.isDefault)
        assertFalse(dao.getTemplate("b")!!.isDefault)

        dao.setDefault("b")
        assertFalse(dao.getTemplate("a")!!.isDefault)
        assertTrue(dao.getTemplate("b")!!.isDefault)

        dao.setDefault("a")
        assertTrue(dao.getTemplate("a")!!.isDefault)
        assertFalse(dao.getTemplate("b")!!.isDefault)
    }

    @Test
    fun observeTemplatesWithModules_ordersByPriorityDescending() = runTest {
        dao.upsertTemplate(template("low", priority = 1))
        dao.upsertTemplate(template("high", priority = 9))

        val ids = dao.observeTemplatesWithModules().first().map { it.template.id }
        assertEquals(listOf("high", "low"), ids)
    }

    @Test
    fun getTemplate_returnsNullWhenMissing() = runTest {
        assertNull(dao.getTemplate("missing"))
    }
}

package com.morningflow.app.core.data

import com.morningflow.app.core.model.Behaviour
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode
import com.morningflow.app.core.model.ModuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class DefaultTemplatesTest {
    @Test
    fun recommendedTemplateUsesCurrentPlannedDurations() {
        assertEquals(1_789, recommendedTemplate().plannedSeconds)
    }

    @Test
    fun recommendedTemplateCoversEveryStageAndBehaviour() {
        val template = recommendedTemplate()
        assertEquals(ModuleType.entries, template.modules.map { it.type })
        assertEquals(
            BehaviourKey.entries.toSet(),
            template.modules.flatMap { it.behaviours }.map(Behaviour::key).toSet(),
        )
    }

    @Test
    fun leaveBedStepsAreLockedAndNothingElseIs() {
        val locked = recommendedTemplate().modules
            .flatMap { it.behaviours }
            .filter(Behaviour::locked)
            .map(Behaviour::key)
            .toSet()
        assertEquals(
            setOf(
                BehaviourKey.LEAVE_BED,
                BehaviourKey.CHANGE_INTO_HOME_CLOTHES,
                BehaviourKey.LIGHT_REMINDER,
            ),
            locked,
        )
    }

    /**
     * A module id is the database's primary key, so two seeded templates sharing a
     * module instance would make one overwrite the other's rows and silently strip
     * stages from a template. Every seeded id must therefore be distinct.
     */
    @Test
    fun seededTemplatesShareNoModuleOrBehaviourIds() {
        val modules = testTemplateSeed().flatMap { it.modules }
        assertEquals(modules.size, modules.map { it.id }.toSet().size)

        val behaviours = modules.flatMap { it.behaviours }
        assertEquals(behaviours.size, behaviours.map { it.id }.toSet().size)
    }

    /** A fresh install ships with Recommended Morning alone; users build the rest. */
    @Test
    fun seedContainsOnlyCurrentStarterTemplates() {
        val seed = defaultTemplateSeed()
        assertEquals(listOf("recommended-30"), seed.map { it.id })
        assertEquals("Recommended Morning", seed.single().name)
        assertTrue(seed.single().isDefault)
    }

    @Test
    fun seedTemplatesHaveDocumentedMetadataAndDurations() {
        val seed = testTemplateSeed().associateBy { it.name }

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), seed.getValue("Recommended Morning").applicableDays)
        assertEquals(LocalTime.of(6, 0), seed.getValue("Recommended Morning").applicableStart)
        assertEquals(5, seed.getValue("Recommended Morning").priority)
        assertEquals(1_789, seed.getValue("Recommended Morning").plannedSeconds)

        assertEquals(LocalTime.of(7, 30), seed.getValue("Later Morning").applicableStart)
        assertEquals(2, seed.getValue("Later Morning").priority)
        assertEquals(1_789, seed.getValue("Later Morning").plannedSeconds)

        val highPriority = seed.getValue("Same Time High Priority")
        assertEquals(LocalTime.of(7, 30), highPriority.applicableStart)
        assertEquals(4, highPriority.priority)
        assertEquals(1_013, highPriority.plannedSeconds)

        val shortSunday = seed.getValue("Short Sunday")
        assertEquals(setOf(DayOfWeek.SUNDAY), shortSunday.applicableDays)
        assertEquals(LocalTime.of(6, 45), shortSunday.applicableStart)
        assertEquals(3, shortSunday.priority)
        assertEquals(70, shortSunday.plannedSeconds)
    }

    @Test
    fun seededTemplateIdsAreUnique() {
        val ids = testTemplateSeed().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun disablingBehavioursShortensTheStageBudget() {
        val module = recommendedTemplate().modules.first { it.type == ModuleType.LEAVE_BED }
        val trimmed = module.copy(
            behaviours = module.behaviours.map {
                if (it.key == BehaviourKey.LIGHT_REMINDER) it.copy(mode = BehaviourMode.OFF) else it
            },
        )
        assertTrue(trimmed.plannedSeconds < module.plannedSeconds)
        assertEquals(
            module.plannedSeconds - BehaviourKey.LIGHT_REMINDER.mediumSeconds,
            trimmed.plannedSeconds,
        )
    }

    @Test
    fun behaviourModesUseProductDurations() {
        val leaveBed = recommendedTemplate().modules.first { it.type == ModuleType.LEAVE_BED }
        assertEquals(40, leaveBed.behaviours.first { it.key == BehaviourKey.LEAVE_BED }.plannedSeconds())
        assertEquals(
            60,
            leaveBed.behaviours.first { it.key == BehaviourKey.LEAVE_BED }
                .copy(mode = BehaviourMode.LONG)
                .plannedSeconds(),
        )
        assertEquals(
            0,
            leaveBed.behaviours.first { it.key == BehaviourKey.LIGHT_REMINDER }
                .copy(mode = BehaviourMode.OFF)
                .plannedSeconds(),
        )
    }

    @Test
    fun sensorModesUseTargetsAndCanBeOff() {
        val physical = recommendedTemplate().modules.first { it.type == ModuleType.PHYSICAL_WAKE_UP }
        val squats = physical.behaviours.first { it.key == BehaviourKey.SQUATS }
        val walking = physical.behaviours.first { it.key == BehaviourKey.WALKING }

        assertEquals(63, squats.plannedSeconds(squatTarget = 10))
        assertEquals(61, walking.plannedSeconds(stepTarget = 50))
        assertEquals(0, squats.copy(mode = BehaviourMode.OFF).plannedSeconds(squatTarget = 10))
        assertEquals(0, walking.copy(mode = BehaviourMode.OFF).plannedSeconds(stepTarget = 50))

        // Stretching is a fixed 105s on top of the two sensor behaviours, which puts the
        // stage at 229s - close to the 4:00 the narration script budgets for it.
        assertEquals(
            105 + 63 + 61,
            physical.copy(squatTarget = 10, stepTarget = 50).plannedSeconds,
        )
    }

    @Test
    fun behaviourModesFollowProductRules() {
        assertThrows(IllegalArgumentException::class.java) {
            Behaviour(key = BehaviourKey.LEAVE_BED, mode = BehaviourMode.OFF)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Behaviour(key = BehaviourKey.CHANGE_INTO_HOME_CLOTHES, mode = BehaviourMode.OFF)
        }
        assertEquals(
            "Change into Comfy Clothes",
            BehaviourKey.CHANGE_INTO_HOME_CLOTHES.label,
        )
        assertEquals(
            BehaviourMode.OFF,
            Behaviour(key = BehaviourKey.LIGHT_REMINDER, mode = BehaviourMode.OFF).mode,
        )
        assertEquals(
            BehaviourMode.OFF,
            Behaviour(key = BehaviourKey.STRETCHING, mode = BehaviourMode.OFF).mode,
        )
        assertEquals(
            BehaviourMode.OFF,
            Behaviour(key = BehaviourKey.SQUATS, mode = BehaviourMode.OFF).mode,
        )
        assertEquals(
            BehaviourMode.OFF,
            Behaviour(key = BehaviourKey.WALKING, mode = BehaviourMode.OFF).mode,
        )
    }

    @Test
    fun disabledModuleHasNoPlannedDuration() {
        val module = recommendedTemplate().modules.first()
        assertEquals(0, module.copy(enabled = false).plannedSeconds)
    }

    @Test
    fun developmentSeedPreservesCriticalModeTargetAndModuleStates() {
        val seed = testTemplateSeed().associateBy { it.name }
        val highPriority = seed.getValue("Same Time High Priority")
        val physical = highPriority.modules.first { it.type == ModuleType.PHYSICAL_WAKE_UP }
        assertEquals(0, physical.squatTarget)
        assertEquals(120, physical.stepTarget)
        assertEquals(BehaviourMode.OFF, physical.behaviours.first { it.key == BehaviourKey.SQUATS }.mode)
        assertEquals(BehaviourMode.ON, physical.behaviours.first { it.key == BehaviourKey.WALKING }.mode)
        assertFalse(highPriority.modules.first { it.type == ModuleType.MEAL_PREPARATION }.enabled)
        assertTrue(highPriority.modules.first { it.type == ModuleType.MEAL_PREPARATION }
            .behaviours.all { it.mode == BehaviourMode.OFF })

        val shortSunday = seed.getValue("Short Sunday")
        assertTrue(shortSunday.modules.first { it.type == ModuleType.LEAVE_BED }.enabled)
        assertEquals(BehaviourMode.OFF, shortSunday.modules.first { it.type == ModuleType.LEAVE_BED }
            .behaviours.first { it.key == BehaviourKey.LIGHT_REMINDER }.mode)
        assertTrue(shortSunday.modules.filterNot { it.type == ModuleType.LEAVE_BED }
            .all { !it.enabled && it.squatTarget == 0 && it.stepTarget == 0 })
    }
}

package com.morningflow.app.feature.templates

import com.morningflow.app.core.data.FakeSettingsRepository
import com.morningflow.app.core.data.FakeTemplateRepository
import com.morningflow.app.core.data.TemplateRepository
import com.morningflow.app.core.data.defaultTemplateSeed
import com.morningflow.app.core.data.testTemplateSeed
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.RoutineTemplate
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEditorLogicTest {
    @Test
    fun templateViewModelStaysLoadingUntilTemplatesArrive() = runTest {
        val repository = DelayedTemplateRepository()
        val viewModel = TemplatesViewModel(repository, FakeSettingsRepository(), backgroundScope)
        runCurrent()

        assertTrue(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.templates.isEmpty())

        repository.publish(defaultTemplateSeed())
        runCurrent()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(defaultTemplateSeed().size, viewModel.state.value.templates.size)
        assertTrue(viewModel.state.value.displayTemplates.isNotEmpty())
    }

    @Test
    fun validation_rejectsRequiredFieldsAndDuplicateName() {
        val existing = listOf(template("Morning"))
        val result = validateTemplateDraft(
            draft = TemplateDraft(
                id = null,
                name = " morning ",
                applicableDays = emptySet(),
                recommendedFrom = LocalTime.of(6, 0),
                priorityText = "0",
                modules = blankTemplateModules(),
                isDefault = false,
            ),
            existing = existing,
            schedule = com.morningflow.app.core.model.GlobalMorningSchedule(
                earliestWakeUp = LocalTime.of(7, 0),
                latestWakeUp = LocalTime.of(8, 0),
                readyBy = LocalTime.of(9, 0),
            ),
        )

        assertFalse(result.isValid)
        assertTrue(TemplateFieldError.NAME_EXISTS in result.errors)
        assertTrue(TemplateFieldError.DAYS_REQUIRED in result.errors)
        assertTrue(TemplateFieldError.PRIORITY_RANGE in result.errors)
        assertTrue(TemplateFieldError.RECOMMENDED_FROM_OUTSIDE_SCHEDULE in result.errors)
        assertEquals("Template name already exists.", result.errors[TemplateFieldError.NAME_EXISTS])
        assertEquals("Priority must be between 1 and 5.", result.errors[TemplateFieldError.PRIORITY_RANGE])
    }

    @Test
    fun validation_rejectsSensorTargetsOnlyWhenSensorIsOn() {
        val modules = blankTemplateModules().map { module ->
            if (module.type == ModuleType.PHYSICAL_WAKE_UP) module.copy(
                enabled = true,
                squatTarget = 0,
                behaviours = module.behaviours.map { behaviour ->
                    if (behaviour.key == BehaviourKey.SQUATS) behaviour.copy(mode = BehaviourMode.ON) else behaviour
                },
            ) else module
        }
        val result = validateTemplateDraft(
            TemplateDraft("id", "Valid", setOf(DayOfWeek.MONDAY), LocalTime.of(7, 0), "1", modules, false),
            emptyList(),
            com.morningflow.app.core.model.GlobalMorningSchedule(),
        )
        assertTrue(TemplateFieldError.SQUAT_TARGET_RANGE in result.errors)
    }

    @Test
    fun validation_reportsNameLengthAndMissingPrioritySeparately() {
        val result = validateTemplateDraft(
            TemplateDraft(
                id = null,
                name = "12345678901234567890123456",
                applicableDays = setOf(DayOfWeek.MONDAY),
                recommendedFrom = LocalTime.of(7, 0),
                priorityText = "",
                modules = blankTemplateModules(),
                isDefault = false,
            ),
            existing = emptyList(),
            schedule = com.morningflow.app.core.model.GlobalMorningSchedule(),
        )
        assertEquals("Template name must be 25 characters or fewer.", result.errors[TemplateFieldError.NAME_TOO_LONG])
        assertEquals("Priority is required.", result.errors[TemplateFieldError.PRIORITY_REQUIRED])
        assertFalse(TemplateFieldError.NAME_EXISTS in result.errors)
    }

    @Test
    fun displayCountsEnabledModulesAndRoundsExactPlannedSeconds() {
        val source = template("Duration").copy(
            modules = blankTemplateModules().map { module ->
                if (module.type == ModuleType.PHYSICAL_WAKE_UP) module.copy(
                    enabled = true,
                    behaviours = module.behaviours.map { behaviour ->
                        if (behaviour.key == BehaviourKey.STRETCHING) behaviour.copy(mode = BehaviourMode.SHORT) else behaviour
                    },
                ) else module
            },
        )
        val display = source.toTemplateSummaryDisplayData()
        assertEquals(2, display.moduleCount)
        assertEquals(kotlin.math.round(source.plannedSeconds / 60.0).toInt(), display.totalDurationMinutes)
    }

    @Test
    fun disablingModuleClearsConfigurationAndReopeningStartsBlank() = runTest {
        val viewModel = TemplatesViewModel(FakeTemplateRepository(), FakeSettingsRepository(), backgroundScope)
        runCurrent()
        viewModel.openEditor()

        viewModel.toggleModule(ModuleType.MEAL_PREPARATION)
        viewModel.updateBehaviour(ModuleType.MEAL_PREPARATION, BehaviourKey.MEAL_PREPARATION, BehaviourMode.SHORT)
        viewModel.toggleModule(ModuleType.MEAL_PREPARATION)

        val disabled = viewModel.editor.value!!.modules.first { it.type == ModuleType.MEAL_PREPARATION }
        assertFalse(disabled.enabled)
        assertTrue(disabled.behaviours.all { it.mode == BehaviourMode.OFF })
        assertEquals(0, disabled.plannedSeconds)

        viewModel.toggleModule(ModuleType.MEAL_PREPARATION)
        val reopened = viewModel.editor.value!!.modules.first { it.type == ModuleType.MEAL_PREPARATION }
        assertTrue(reopened.enabled)
        assertTrue(reopened.behaviours.all { it.mode == BehaviourMode.OFF })
        assertEquals(0, reopened.plannedSeconds)
    }

    @Test
    fun viewModel_createsTemplateAndProtectsRecommendedMorningFromDelete() = runTest {
        val repository = FakeTemplateRepository()
        val viewModel = TemplatesViewModel(repository, FakeSettingsRepository(), backgroundScope)
        runCurrent()

        viewModel.openEditor()
        viewModel.updateName("New Morning")
        viewModel.toggleDay(DayOfWeek.MONDAY)
        viewModel.updateRecommendedFrom(LocalTime.of(7, 0))
        viewModel.updatePriority("2")
        viewModel.saveEditor()
        runCurrent()

        assertTrue(repository.templates.value.any { it.name == "New Morning" })
        viewModel.delete("recommended-30")
        runCurrent()
        assertTrue(repository.templates.value.any { it.id == "recommended-30" })
    }

    @Test
    fun viewModel_failedSaveKeepsDraft() = runTest {
        val repository = FailingTemplateRepository()
        val viewModel = TemplatesViewModel(repository, FakeSettingsRepository(), backgroundScope)
        runCurrent()
        viewModel.openEditor()
        viewModel.updateName("Draft")
        viewModel.toggleDay(DayOfWeek.MONDAY)
        viewModel.updateRecommendedFrom(LocalTime.of(7, 0))
        viewModel.updatePriority("2")
        viewModel.saveEditor()
        runCurrent()

        assertEquals("Draft", viewModel.editor.value?.name)
        assertFalse(viewModel.editor.value?.isSaving ?: false)
        assertEquals(1, repository.saveCalls)
    }

    @Test
    fun viewModel_editsExistingTemplateWithoutChangingItsId() = runTest {
        val repository = FakeTemplateRepository(testTemplateSeed())
        val viewModel = TemplatesViewModel(repository, FakeSettingsRepository(), backgroundScope)
        runCurrent()
        viewModel.openEditor("dev-later-morning")
        viewModel.updateName("Edited Morning")
        viewModel.updateRecommendedFrom(LocalTime.of(7, 30))
        viewModel.updatePriority("3")
        viewModel.saveEditor()
        runCurrent()

        assertEquals("Edited Morning", repository.templates.value.first { it.id == "dev-later-morning" }.name)
        assertEquals(4, repository.templates.value.size)
    }

    @Test
    fun recommendedMorningNameIsReadOnlyAndCannotLoseItsDefault() = runTest {
        val repository = FakeTemplateRepository()
        val viewModel = TemplatesViewModel(repository, FakeSettingsRepository(), backgroundScope)
        runCurrent()
        viewModel.openEditor("recommended-30")
        viewModel.updateName("Changed")
        viewModel.updateRecommendedFrom(LocalTime.of(7, 0))
        viewModel.updateDefault(false)
        viewModel.saveEditor()
        runCurrent()

        val recommended = repository.templates.value.first { it.id == "recommended-30" }
        assertEquals("Recommended Morning", recommended.name)
        assertTrue(recommended.isDefault)
    }

    @Test
    fun cancellingUserDefaultRestoresRecommendedMorning() = runTest {
        val repository = FakeTemplateRepository(testTemplateSeed())
        val settings = FakeSettingsRepository()
        val viewModel = TemplatesViewModel(repository, settings, backgroundScope)
        runCurrent()
        repository.setDefault("dev-later-morning")
        runCurrent()
        viewModel.openEditor("dev-later-morning")
        viewModel.updateDefault(false)
        viewModel.updateRecommendedFrom(LocalTime.of(7, 30))
        viewModel.saveEditor()
        runCurrent()

        assertTrue(repository.templates.value.first { it.id == "recommended-30" }.isDefault)
        assertEquals(1, repository.templates.value.count { it.isDefault })
    }

    @Test
    fun deleteConfirmationCancelKeepsTemplate() = runTest {
        val repository = FakeTemplateRepository(testTemplateSeed())
        val viewModel = TemplatesViewModel(repository, FakeSettingsRepository(), backgroundScope)
        runCurrent()
        viewModel.requestDelete("dev-later-morning")
        assertEquals("dev-later-morning", viewModel.state.value.pendingDeleteId)
        viewModel.dismissDelete()
        assertEquals(null, viewModel.state.value.pendingDeleteId)
        assertTrue(repository.templates.value.any { it.id == "dev-later-morning" })
    }

    @Test
    fun unsavedEditorBackRequestsKeepEditingOrDiscard() = runTest {
        val repository = FakeTemplateRepository()
        val viewModel = TemplatesViewModel(repository, FakeSettingsRepository(), backgroundScope)
        runCurrent()
        viewModel.openEditor()
        viewModel.updateName("Draft")
        viewModel.requestCloseEditor()
        assertTrue(viewModel.editor.value?.showDiscardConfirmation == true)
        viewModel.keepEditing()
        assertFalse(viewModel.editor.value?.showDiscardConfirmation ?: true)
        viewModel.requestCloseEditor()
        viewModel.discardEditor()
        assertEquals(null, viewModel.editor.value)
    }

    @Test
    fun createEditorKeepsRecommendedFromUnsetUntilSelected() = runTest {
        val viewModel = TemplatesViewModel(FakeTemplateRepository(), FakeSettingsRepository(), backgroundScope)
        runCurrent()
        viewModel.openEditor()
        assertEquals("", viewModel.editor.value?.recommendedFromText)
        assertEquals(null, viewModel.editor.value?.recommendedFrom)

        viewModel.updateRecommendedFrom(LocalTime.of(7, 15))

        assertEquals("07:15", viewModel.editor.value?.recommendedFromText)
        assertEquals(LocalTime.of(7, 15), viewModel.editor.value?.recommendedFrom)
    }

    @Test
    fun retryAfterSetDefaultFailureReusesSavedTemplateId() = runTest {
        val repository = SaveThenDefaultFailureRepository()
        val viewModel = TemplatesViewModel(repository, FakeSettingsRepository(), backgroundScope)
        runCurrent()
        viewModel.openEditor()
        viewModel.updateName("Retryable")
        viewModel.toggleDay(DayOfWeek.MONDAY)
        viewModel.updateRecommendedFromText("07:00")
        viewModel.updatePriority("2")
        viewModel.updateDefault(true)
        viewModel.saveEditor()
        runCurrent()

        val savedId = viewModel.editor.value?.templateId
        assertTrue(savedId != null)
        assertEquals(1, repository.saveCalls)
        assertTrue(viewModel.editor.value?.pendingDefaultSync == true)
        assertEquals(1, repository.setDefaultCalls)
        assertEquals(savedId, repository.setDefaultIds.single())
        repository.failSetDefault = false
        viewModel.saveEditor()
        runCurrent()

        assertEquals(1, repository.templates.value.count { it.name == "Retryable" })
        assertEquals(2, repository.saveCalls)
        assertEquals(2, repository.setDefaultCalls)
        assertEquals(savedId, repository.setDefaultIds.last())
        assertEquals(null, viewModel.editor.value)
    }

    @Test
    fun priorityConflictRequiresOverlappingApplicableDays() {
        val existing = template("Existing").copy(priority = 2, applicableDays = setOf(DayOfWeek.MONDAY))
        val nonOverlapping = validateTemplateDraft(
            TemplateDraft("new", "New", setOf(DayOfWeek.TUESDAY), LocalTime.of(7, 0), "2", blankTemplateModules(), false),
            listOf(existing),
            com.morningflow.app.core.model.GlobalMorningSchedule(),
        )
        val overlapping = validateTemplateDraft(
            TemplateDraft("new", "New", setOf(DayOfWeek.MONDAY), LocalTime.of(7, 0), "2", blankTemplateModules(), false),
            listOf(existing),
            com.morningflow.app.core.model.GlobalMorningSchedule(),
        )

        assertFalse(TemplateFieldError.PRIORITY_CONFLICT in nonOverlapping.errors)
        assertTrue(TemplateFieldError.PRIORITY_CONFLICT in overlapping.errors)
    }

    @Test
    fun bothSensorTargetErrorsAreReportedAndOffSensorsCannotAdjustTargets() = runTest {
        val modules = blankTemplateModules().map { module ->
            if (module.type == ModuleType.PHYSICAL_WAKE_UP) module.copy(
                enabled = true,
                behaviours = module.behaviours.map { behaviour ->
                    if (behaviour.key == BehaviourKey.SQUATS || behaviour.key == BehaviourKey.WALKING) {
                        behaviour.copy(mode = BehaviourMode.ON)
                    } else behaviour
                },
            ) else module
        }
        val validation = validateTemplateDraft(
            TemplateDraft("id", "Valid", setOf(DayOfWeek.MONDAY), LocalTime.of(7, 0), "1", modules, false),
            emptyList(),
            com.morningflow.app.core.model.GlobalMorningSchedule(),
        )
        assertTrue(TemplateFieldError.SQUAT_TARGET_RANGE in validation.errors)
        assertTrue(TemplateFieldError.WALKING_TARGET_RANGE in validation.errors)

        val viewModel = TemplatesViewModel(FakeTemplateRepository(), FakeSettingsRepository(), backgroundScope)
        runCurrent()
        viewModel.openEditor()
        viewModel.toggleModule(ModuleType.PHYSICAL_WAKE_UP)
        viewModel.adjustTarget(ModuleType.PHYSICAL_WAKE_UP, BehaviourKey.SQUATS, 1)
        assertEquals(0, viewModel.editor.value!!.modules.first { it.type == ModuleType.PHYSICAL_WAKE_UP }.squatTarget)
        viewModel.updateBehaviour(ModuleType.PHYSICAL_WAKE_UP, BehaviourKey.SQUATS, BehaviourMode.ON)
        viewModel.adjustTarget(ModuleType.PHYSICAL_WAKE_UP, BehaviourKey.SQUATS, 1)
        assertEquals(1, viewModel.editor.value!!.modules.first { it.type == ModuleType.PHYSICAL_WAKE_UP }.squatTarget)
    }

    @Test
    fun failedDeleteKeepsConfirmationTargetAndReportsOperationError() = runTest {
        val repository = FailingDeleteRepository()
        val viewModel = TemplatesViewModel(repository, FakeSettingsRepository(), backgroundScope)
        runCurrent()
        viewModel.requestDelete("user-template")
        viewModel.confirmDelete()
        runCurrent()

        assertEquals("user-template", viewModel.state.value.pendingDeleteId)
        assertEquals("delete failed", viewModel.state.value.operationError)
        assertTrue(repository.templates.value.any { it.id == "user-template" })
    }

    private fun template(name: String) = RoutineTemplate(
        id = name,
        name = name,
        applicableDays = setOf(DayOfWeek.MONDAY),
        applicableStart = LocalTime.of(7, 0),
        modules = blankTemplateModules(),
    )
}

private class DelayedTemplateRepository : TemplateRepository {
    private val state = MutableStateFlow<List<RoutineTemplate>>(emptyList())
    override val templates: StateFlow<List<RoutineTemplate>> = state

    fun publish(value: List<RoutineTemplate>) {
        state.value = value
    }

    override suspend fun awaitReady() = Unit
    override suspend fun save(template: RoutineTemplate) = Unit
    override suspend fun delete(templateId: String) = Unit
    override suspend fun setDefault(templateId: String) = Unit
}

private class FailingTemplateRepository : com.morningflow.app.core.data.TemplateRepository {
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(listOf(templateSeed()))
    var saveCalls: Int = 0
    override val templates = flow
    override suspend fun awaitReady() = Unit
    override suspend fun save(template: RoutineTemplate) { saveCalls += 1; error("save failed") }
    override suspend fun delete(templateId: String) = Unit
    override suspend fun setDefault(templateId: String) = Unit

    private fun templateSeed() = RoutineTemplate(
        id = "seed",
        name = "Seed",
        applicableDays = setOf(DayOfWeek.MONDAY),
        applicableStart = LocalTime.of(7, 0),
        modules = blankTemplateModules(),
    )
}

private class SaveThenDefaultFailureRepository : com.morningflow.app.core.data.TemplateRepository {
    private val flow = kotlinx.coroutines.flow.MutableStateFlow<List<RoutineTemplate>>(emptyList())
    var failSetDefault = true
    var saveCalls = 0
    var setDefaultCalls = 0
    val setDefaultIds = mutableListOf<String>()
    override val templates = flow
    override suspend fun awaitReady() = Unit

    override suspend fun save(template: RoutineTemplate) {
        saveCalls += 1
        flow.value = flow.value.filterNot { it.id == template.id } + template
    }

    override suspend fun delete(templateId: String) = Unit

    override suspend fun setDefault(templateId: String) {
        setDefaultCalls += 1
        setDefaultIds += templateId
        if (failSetDefault) error("set default failed")
        flow.value = flow.value.map { it.copy(isDefault = it.id == templateId) }
    }
}

private class FailingDeleteRepository : com.morningflow.app.core.data.TemplateRepository {
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(
        listOf(
            RoutineTemplate(
                id = "user-template",
                name = "User Template",
                applicableDays = setOf(DayOfWeek.MONDAY),
                applicableStart = LocalTime.of(7, 0),
                modules = blankTemplateModules(),
            ),
        ),
    )
    override val templates = flow
    override suspend fun awaitReady() = Unit
    override suspend fun save(template: RoutineTemplate) = Unit
    override suspend fun delete(templateId: String) = error("delete failed")
    override suspend fun setDefault(templateId: String) = Unit
}

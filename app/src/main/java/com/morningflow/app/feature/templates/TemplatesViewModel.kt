package com.morningflow.app.feature.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningflow.app.core.data.SettingsRepository
import com.morningflow.app.core.data.TemplateRepository
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.RECOMMENDED_TEMPLATE_ID
import com.morningflow.app.core.model.RoutineTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.UUID

data class TemplatesUiState(
    val isLoading: Boolean = true,
    val templates: List<RoutineTemplate> = emptyList(),
    val operationError: String? = null,
    val pendingDeleteId: String? = null,
) {
    val displayTemplates: List<TemplateSummaryDisplayData>
        get() = templates.mapIndexed { index, template -> template.toTemplateSummaryDisplayData(index + 1) }
}

data class TemplateEditorState(
    val templateId: String? = null,
    val name: String = "",
    val applicableDays: Set<java.time.DayOfWeek> = emptySet(),
    val recommendedFrom: LocalTime? = null,
    val recommendedFromText: String = "",
    val priorityText: String = "",
    val isDefault: Boolean = false,
    val modules: List<com.morningflow.app.core.model.RoutineModule> = blankTemplateModules(),
    val errors: Map<TemplateFieldError, String> = emptyMap(),
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val pendingDefaultSync: Boolean = false,
) {
    val isEditing: Boolean get() = templateId != null
}

fun TemplateEditorState.toDisplayData(): TemplateEditorDisplayData {
    val displayTemplate = RoutineTemplate(
        id = templateId ?: "draft",
        name = name,
        applicableDays = applicableDays,
        applicableStart = recommendedFrom ?: LocalTime.MIDNIGHT,
        modules = modules,
        isDefault = isDefault,
        priority = priorityText.toIntOrNull() ?: 0,
    ).toTemplateSummaryDisplayData()
    return TemplateEditorDisplayData(
        template = displayTemplate,
        recommendedFrom = recommendedFrom ?: LocalTime.MIDNIGHT,
        recommendedFromText = recommendedFromText,
        priority = priorityText.toIntOrNull() ?: 0,
        priorityText = priorityText,
        isDefault = isDefault,
        isDeletable = templateId != RECOMMENDED_TEMPLATE_ID,
        templateNameErrorText = errors.firstMessage(
            TemplateFieldError.NAME_REQUIRED,
            TemplateFieldError.NAME_TOO_LONG,
            TemplateFieldError.NAME_EXISTS,
        ),
        priorityErrorText = errors.firstMessage(
            TemplateFieldError.PRIORITY_REQUIRED,
            TemplateFieldError.PRIORITY_RANGE,
            TemplateFieldError.PRIORITY_CONFLICT,
        ),
        applicableDaysError = errors[TemplateFieldError.DAYS_REQUIRED],
        recommendedFromError = errors[TemplateFieldError.RECOMMENDED_FROM_REQUIRED]
            ?: errors[TemplateFieldError.RECOMMENDED_FROM_OUTSIDE_SCHEDULE],
        squatTargetError = errors[TemplateFieldError.SQUAT_TARGET_RANGE],
        walkingTargetError = errors[TemplateFieldError.WALKING_TARGET_RANGE],
    )
}

private fun Map<TemplateFieldError, String>.firstMessage(vararg fields: TemplateFieldError): String? =
    fields.firstNotNullOfOrNull { this[it] }

/** Product logic for Templates. It only consumes the existing repository contracts. */
class TemplatesViewModel(
    private val templateRepository: TemplateRepository,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val operationScope = scope ?: viewModelScope
    private val _state = MutableStateFlow(TemplatesUiState())
    val state: StateFlow<TemplatesUiState> = _state.asStateFlow()

    private val _editor = MutableStateFlow<TemplateEditorState?>(null)
    val editor: StateFlow<TemplateEditorState?> = _editor.asStateFlow()

    private val _editorClosedBySave = MutableStateFlow(false)
    val editorClosedBySave: StateFlow<Boolean> = _editorClosedBySave.asStateFlow()

    private var settings: AppSettings = settingsRepository.settings.value

    init {
        operationScope.launch {
            settingsRepository.settings.collect { settings = it }
        }
        operationScope.launch {
            templateRepository.templates.collect { templates ->
                // RoomTemplateRepository emits an empty initial value while its seed/repair
                // work is running. Keep Loading until a real template set arrives.
                if (templates.isNotEmpty()) {
                    _state.update { it.copy(isLoading = false, templates = templates) }
                }
            }
        }
    }

    fun openEditor(templateId: String? = null) {
        _editorClosedBySave.value = false
        val template = templateId?.let { id -> _state.value.templates.firstOrNull { it.id == id } }
        _editor.value = if (template == null) {
            TemplateEditorState(
                recommendedFrom = null,
                recommendedFromText = "",
                modules = blankTemplateModules(),
            )
        } else {
            TemplateEditorState(
                templateId = template.id,
                name = template.name,
                applicableDays = template.applicableDays,
                recommendedFrom = template.applicableStart,
                recommendedFromText = template.applicableStart.toTemplateInputText(),
                priorityText = template.priority.toString(),
                isDefault = template.isDefault,
                modules = template.modules,
            )
        }
    }

    fun closeEditor() { _editor.value = null }

    fun requestCloseEditor() {
        val current = _editor.value ?: return
        if (current.hasUnsavedChanges && !current.isSaving) {
            _editor.value = current.copy(showDiscardConfirmation = true)
        } else if (!current.isSaving) {
            closeEditor()
        }
    }

    fun keepEditing() = updateEditor { it.copy(showDiscardConfirmation = false) }

    fun discardEditor() {
        if (_editor.value?.isSaving == false) closeEditor()
    }

    fun updateName(value: String) = updateEditor {
        if (it.templateId == RECOMMENDED_TEMPLATE_ID) it
        else it.copy(name = value, errors = it.errors - setOf(
            TemplateFieldError.NAME_REQUIRED,
            TemplateFieldError.NAME_TOO_LONG,
            TemplateFieldError.NAME_EXISTS,
        ), hasUnsavedChanges = true)
    }

    fun updatePriority(value: String) = updateEditor {
        it.copy(
            priorityText = value,
            errors = it.errors - setOf(
                TemplateFieldError.PRIORITY_REQUIRED,
                TemplateFieldError.PRIORITY_RANGE,
                TemplateFieldError.PRIORITY_CONFLICT,
            ),
            hasUnsavedChanges = true,
        )
    }

    fun toggleDay(day: java.time.DayOfWeek) = updateEditor {
        it.copy(
            applicableDays = if (day in it.applicableDays) it.applicableDays - day else it.applicableDays + day,
            errors = it.errors - TemplateFieldError.DAYS_REQUIRED,
            hasUnsavedChanges = true,
        )
    }

    fun updateRecommendedFrom(value: LocalTime?) = updateEditor {
        it.copy(
            recommendedFrom = value,
            recommendedFromText = value?.toTemplateInputText().orEmpty(),
            errors = it.errors - TemplateFieldError.RECOMMENDED_FROM_REQUIRED,
            hasUnsavedChanges = true,
        )
    }

    fun updateRecommendedFromText(value: String) = updateEditor {
        it.copy(
            recommendedFrom = parseTemplateTime(value),
            recommendedFromText = value,
            errors = it.errors - TemplateFieldError.RECOMMENDED_FROM_REQUIRED,
            hasUnsavedChanges = true,
        )
    }

    fun updateDefault(value: Boolean) = updateEditor {
        if (it.templateId == RECOMMENDED_TEMPLATE_ID && it.isDefault && !value) it
        else it.copy(isDefault = value, hasUnsavedChanges = true)
    }

    fun toggleModule(type: com.morningflow.app.core.model.ModuleType) = updateEditor { editor ->
        editor.copy(hasUnsavedChanges = true, modules = editor.modules.map { module ->
            if (module.type != type || type == com.morningflow.app.core.model.ModuleType.LEAVE_BED) module
            else if (module.enabled) module.copy(enabled = false, squatTarget = 0, stepTarget = 0,
                behaviours = module.behaviours.map { behaviour -> behaviour.copy(mode = com.morningflow.app.core.model.BehaviourMode.OFF) })
            else module.copy(enabled = true, squatTarget = 0, stepTarget = 0,
                behaviours = module.behaviours.map { behaviour -> behaviour.copy(mode = com.morningflow.app.core.model.BehaviourMode.OFF) })
        })
    }

    fun updateBehaviour(type: com.morningflow.app.core.model.ModuleType, key: com.morningflow.app.core.model.BehaviourKey,
        mode: com.morningflow.app.core.model.BehaviourMode) = updateEditor { editor ->
        editor.copy(hasUnsavedChanges = true, modules = editor.modules.map { module ->
            if (module.type != type || !module.enabled) module else module.copy(
                behaviours = module.behaviours.map { behaviour -> if (behaviour.key == key) behaviour.copy(mode = mode) else behaviour },
            )
        })
    }

    fun cycleBehaviour(type: com.morningflow.app.core.model.ModuleType, key: com.morningflow.app.core.model.BehaviourKey) {
        val current = _editor.value?.modules?.firstOrNull { it.type == type }?.behaviours
            ?.firstOrNull { it.key == key }?.mode ?: return
        val modes = when {
            key == com.morningflow.app.core.model.BehaviourKey.LEAVE_BED ||
                key == com.morningflow.app.core.model.BehaviourKey.CHANGE_INTO_HOME_CLOTHES ->
                listOf(com.morningflow.app.core.model.BehaviourMode.SHORT, com.morningflow.app.core.model.BehaviourMode.MEDIUM, com.morningflow.app.core.model.BehaviourMode.LONG)
            key.isSensor -> listOf(com.morningflow.app.core.model.BehaviourMode.ON, com.morningflow.app.core.model.BehaviourMode.OFF)
            else -> listOf(com.morningflow.app.core.model.BehaviourMode.SHORT, com.morningflow.app.core.model.BehaviourMode.MEDIUM, com.morningflow.app.core.model.BehaviourMode.LONG, com.morningflow.app.core.model.BehaviourMode.OFF)
        }
        updateBehaviour(type, key, modes[(modes.indexOf(current) + 1) % modes.size])
    }

    fun adjustTarget(type: com.morningflow.app.core.model.ModuleType, key: com.morningflow.app.core.model.BehaviourKey, delta: Int) = updateEditor { editor ->
        val currentModule = editor.modules.firstOrNull { it.type == type }
        val targetBehaviour = currentModule?.behaviours?.firstOrNull { it.key == key }
        if (currentModule?.enabled != true || targetBehaviour?.enabled != true) {
            editor
        } else {
            editor.copy(hasUnsavedChanges = true, modules = editor.modules.map { module ->
                if (module.type != type) module else when (key) {
                    com.morningflow.app.core.model.BehaviourKey.SQUATS -> module.copy(squatTarget = (module.squatTarget + delta).coerceIn(0, 30))
                    com.morningflow.app.core.model.BehaviourKey.WALKING -> module.copy(stepTarget = (module.stepTarget + delta * 10).coerceIn(0, 500))
                    else -> module
                }
            })
        }
    }

    fun saveEditor() {
        val draft = _editor.value ?: return
        if (draft.isSaving) return
        val recommendedFrom = parseTemplateTime(draft.recommendedFromText)
        val validation = validateTemplateDraft(
            draft = TemplateDraft(draft.templateId, draft.name, draft.applicableDays, recommendedFrom, draft.priorityText, draft.modules, draft.isDefault),
            existing = _state.value.templates,
            schedule = settings.schedule,
        )
        if (!validation.isValid) {
            _editor.value = draft.copy(errors = validation.errors, isSaving = false)
            return
        }
        val priority = draft.priorityText.toInt()
        val existing = draft.templateId?.let { id -> _state.value.templates.firstOrNull { it.id == id } }
        val templateId = existing?.id ?: draft.templateId ?: UUID.randomUUID().toString()
        val template = RoutineTemplate(
            id = templateId,
            name = if (existing?.id == RECOMMENDED_TEMPLATE_ID) existing.name else draft.name.trim(),
            applicableDays = draft.applicableDays,
            applicableStart = recommendedFrom!!,
            applicableEnd = existing?.applicableEnd ?: settings.schedule.latestWakeUp,
            priority = priority,
            modules = normalizeTemplateModules(draft.modules),
            isDefault = existing?.isDefault == true || draft.isDefault,
        )
        _editor.value = draft.copy(templateId = template.id, isSaving = true, errors = emptyMap())
        operationScope.launch {
            try {
                templateRepository.save(template)
            } catch (error: Throwable) {
                _editor.value = draft.copy(templateId = template.id, isSaving = false)
                _state.update { it.copy(operationError = error.message ?: "Unable to save template.") }
                return@launch
            }
            try {
                when {
                    existing?.isDefault == true && !draft.isDefault -> templateRepository.setDefault(RECOMMENDED_TEMPLATE_ID)
                    template.isDefault -> templateRepository.setDefault(template.id)
                }
                _editor.value = null
                _editorClosedBySave.value = true
                _state.update { it.copy(operationError = null) }
            } catch (error: Throwable) {
                _editor.value = draft.copy(
                    templateId = template.id,
                    isSaving = false,
                    hasUnsavedChanges = false,
                    pendingDefaultSync = true,
                )
                _state.update { it.copy(operationError = error.message ?: "Unable to save template.") }
            }
        }
    }

    fun setDefault(templateId: String) {
        operationScope.launch {
            try {
                templateRepository.setDefault(templateId)
                _state.update { it.copy(operationError = null) }
            }
            catch (error: Throwable) { _state.update { it.copy(operationError = error.message ?: "Unable to set default template.") } }
        }
    }

    fun delete(templateId: String) {
        val target = _state.value.templates.firstOrNull { it.id == templateId } ?: return
        if (!target.isDeletable) return
        operationScope.launch {
            try {
                templateRepository.delete(templateId)
                _state.update { it.copy(pendingDeleteId = null, operationError = null) }
            } catch (error: Throwable) {
                _state.update { it.copy(operationError = error.message ?: "Unable to delete template.") }
            }
        }
    }

    fun requestDelete(templateId: String) {
        val target = _state.value.templates.firstOrNull { it.id == templateId } ?: return
        if (target.isDeletable) _state.update { it.copy(pendingDeleteId = templateId) }
    }

    fun dismissDelete() { _state.update { it.copy(pendingDeleteId = null) } }

    fun confirmDelete() {
        val id = _state.value.pendingDeleteId ?: return
        delete(id)
    }

    fun clearError() { _state.update { it.copy(operationError = null) } }

    private fun updateEditor(transform: (TemplateEditorState) -> TemplateEditorState) {
        _editor.value = _editor.value?.takeUnless { it.isSaving }?.let(transform)
    }
}

package com.morningflow.app.feature.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.form.DayChip
import com.morningflow.app.core.designsystem.component.form.ModuleSwitch
import com.morningflow.app.core.designsystem.component.form.ModuleSwitchSize
import com.morningflow.app.core.designsystem.component.navigation.EditorTopBar
import com.morningflow.app.core.designsystem.component.template.BehaviorInformationRow
import com.morningflow.app.core.designsystem.component.template.TemplateCardSurface
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.designsystem.time.ClockFormat
import com.morningflow.app.core.designsystem.time.MorningFlowTimePickerDialog
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.ModuleType
import java.time.DayOfWeek
import java.time.LocalTime

/** One built-in step as the plain editor card lists it, with the lengths it can switch to. */
private data class TemplateBehavior(
    val label: String,
    val value: String,
    val options: List<String> = emptyList(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorScreen(
    data: TemplateEditorDisplayData,
    variant: TemplateEditorVariant,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onTemplateNameChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onRecommendedFromChange: (LocalTime) -> Unit,
    onDefaultChange: (Boolean) -> Unit,
    onDayClick: (DayOfWeek) -> Unit,
    onModuleToggle: (ModuleType) -> Unit,
    onBehaviourClick: (ModuleType, BehaviourKey) -> Unit,
    onBehaviourSelected: (ModuleType, BehaviourKey, String) -> Unit,
    onTargetChange: (ModuleType, BehaviourKey, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Spacer(Modifier.height(22.dp))
        EditorTopBar(
            title = stringResource(R.string.template_top_bar_title),
            action = stringResource(R.string.template_save),
            onBack = onBack,
            onAction = onSave,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = MorningFlowSpacing.Space24,
                    top = MorningFlowSpacing.Space16,
                    end = MorningFlowSpacing.Space24,
                    bottom = MorningFlowSpacing.Space24,
                ),
            verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space16),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
            ) {
                TemplateEditorTextField(
                    label = stringResource(R.string.template_name_label),
                    value = data.template.title,
                    onValueChange = onTemplateNameChange,
                    errorMessage = data.templateNameErrorText,
                    enabled = data.isDeletable,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
                ) {
                    Text(
                        text = stringResource(R.string.template_applicable_days_label),
                        color = colors.brandPrimary,
                        style = typography.fieldLabel,
                    )
                    data.applicableDaysError?.let { error ->
                        Text(text = error, color = colors.warning, style = typography.smallRegular)
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
                        verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space6),
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            DayChip(
                                label = day.shortLabel,
                                selected = day in data.template.applicableDays,
                                onClick = { onDayClick(day) },
                            )
                        }
                    }
                }

                TemplateEditorTimePickerField(
                    label = stringResource(R.string.template_recommended_from_label),
                    value = data.recommendedFrom.takeIf { data.recommendedFromText.isNotBlank() },
                    onValueChange = onRecommendedFromChange,
                    errorMessage = data.recommendedFromError,
                    modifier = Modifier.fillMaxWidth(),
                )

                TemplatePriorityDropdown(
                    value = data.priorityText,
                    errorMessage = data.priorityErrorText,
                    onValueChange = onPriorityChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.template_priority_help),
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.textSecondary,
                    style = typography.smallRegular,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.template_default_label),
                        color = colors.textPrimary,
                        style = typography.fieldLabel,
                    )
                    ModuleSwitch(
                        ModuleSwitchSize.Compact,
                        checked = data.isDefault,
                        onClick = { onDefaultChange(!data.isDefault) },
                        label = stringResource(R.string.template_default_label),
                    )
                }
            }

            HorizontalDivider(color = colors.divider)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.template_planned_duration),
                    color = colors.brandPrimary,
                    style = typography.sectionHeading,
                )
                Text(
                    text = data.template.totalDurationMinutes.toEditorDurationText(),
                    color = colors.textPrimary,
                    style = typography.sectionHeading,
                )
            }
            Text(
                text = stringResource(R.string.template_behavior_duration_help),
                modifier = Modifier.fillMaxWidth(),
                color = colors.textSecondary,
                style = typography.smallRegular,
            )

            data.template.modules.forEachIndexed { index, module ->
                if (module.alwaysOn || module.supportsExtendedContent) {
                    ExtendedTemplateModuleCard(
                        module = module,
                        onModuleToggle = { module.type?.let(onModuleToggle) },
                        onBehaviourClick = { key ->
                            module.type?.let { type -> onBehaviourClick(type, key) }
                        },
                        onBehaviourSelected = { key, option ->
                            module.type?.let { type -> onBehaviourSelected(type, key, option) }
                        },
                        onTargetChange = { key, delta ->
                            module.type?.let { type -> onTargetChange(type, key, delta) }
                        },
                        squatTargetError = data.squatTargetError,
                        walkingTargetError = data.walkingTargetError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    TemplateEditorModuleCard(
                        title = module.title,
                        duration = module.durationMinutes.toEditorDurationText(),
                        behaviors = module.behaviors.map { behavior ->
                            TemplateBehavior(behavior.label, behavior.value, behavior.key?.let(::behaviourOptions).orEmpty())
                        },
                        moduleEnabled = module.enabled,
                        onModuleToggle = { module.type?.let(onModuleToggle) },
                        onBehaviorClick = { label ->
                            module.type?.let { type ->
                                module.behaviors.firstOrNull { it.label == label }?.key?.let { key -> onBehaviourClick(type, key) }
                            }
                        },
                        onBehaviorOptionSelected = { label, option ->
                            module.type?.let { type ->
                                module.behaviors.firstOrNull { it.label == label }?.key?.let { key ->
                                    onBehaviourSelected(type, key, option)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplatePriorityDropdown(
    value: String,
    errorMessage: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("1", "2", "3", "4", "5")
    val displayValue = value.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.template_priority_placeholder)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8)) {
        Text(
            text = stringResource(R.string.template_priority_label),
            color = colors.brandPrimary,
            style = typography.fieldLabel,
        )
        errorMessage?.let { error ->
            Text(
                text = error,
                modifier = Modifier.fillMaxWidth(),
                color = colors.warning,
                style = typography.fieldError,
            )
        }
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(1.dp, colors.textPrimary, MorningFlowShapes.Radius8)
                    .clickable { expanded = true }
                    .padding(horizontal = MorningFlowSpacing.Space16),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = displayValue,
                    color = if (value.isBlank()) colors.textSecondary else colors.textPrimary,
                    style = typography.fieldValue,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onValueChange(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateEditorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    errorMessage: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography

    Column(
        modifier = modifier.width(342.dp),
        verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
    ) {
        Text(text = label, color = colors.brandPrimary, style = typography.fieldLabel)
        errorMessage?.let { error ->
            Text(text = error, color = colors.warning, style = typography.body)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = !enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(1.dp, colors.textPrimary, MorningFlowShapes.Radius8)
                .padding(horizontal = MorningFlowSpacing.Space16),
            singleLine = true,
            textStyle = typography.fieldValue.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.brandPrimary),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun TemplateEditorTimePickerField(
    label: String,
    value: LocalTime?,
    onValueChange: (LocalTime) -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        MorningFlowTimePickerDialog(
            initial = value ?: LocalTime.of(7, 0),
            title = label,
            onConfirm = {
                showPicker = false
                onValueChange(it)
            },
            onDismiss = { showPicker = false },
        )
    }

    Column(
        modifier = modifier
            .width(326.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.a11y_change_time)) { showPicker = true },
    ) {
        Text(
            text = label,
            color = colors.brandPrimary,
            style = MorningFlowDesignSystem.typography.timeFieldLabel,
        )
        errorMessage?.let { error ->
            Text(
                text = error,
                color = colors.warning,
                style = MorningFlowDesignSystem.typography.fieldError,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = value?.let(ClockFormat::format) ?: "--:--",
                color = colors.textPrimary,
                style = MorningFlowDesignSystem.typography.timeDisplay,
            )
        }
    }
}

private val TemplateModuleDisplayData.supportsExtendedContent: Boolean
    get() = behaviors.any { it.supportingText != null } || targets.isNotEmpty()

private fun behaviourOptions(key: BehaviourKey): List<String> = when {
    key == BehaviourKey.LEAVE_BED || key == BehaviourKey.CHANGE_INTO_HOME_CLOTHES -> listOf("Short", "Medium", "Long")
    key.isSensor -> listOf("On", "Off")
    else -> listOf("Short", "Medium", "Long", "Off")
}

@Composable
private fun ExtendedTemplateModuleCard(
    module: TemplateModuleDisplayData,
    onModuleToggle: () -> Unit,
    onBehaviourClick: (BehaviourKey) -> Unit,
    onBehaviourSelected: (BehaviourKey, String) -> Unit,
    onTargetChange: (BehaviourKey, Int) -> Unit,
    squatTargetError: String?,
    walkingTargetError: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    val behaviourAreaAlpha = if (module.enabled) 1f else 0.45f

    TemplateCardSurface(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space4)) {
                Text(text = module.title, color = colors.textPrimary, style = typography.sectionHeading)
                Text(
                    text = stringResource(if (module.enabled) R.string.template_module_on else R.string.template_module_off),
                    color = colors.textSecondary,
                    style = typography.smallRegular,
                )
            }
            if (module.alwaysOn) {
                Box(
                    modifier = Modifier
                        .background(colors.brandPrimary, MorningFlowShapes.Radius12)
                        .padding(horizontal = MorningFlowSpacing.Space10, vertical = MorningFlowSpacing.Space4),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.template_always_on),
                        color = colors.background,
                        style = typography.actionLabel,
                    )
                }
            } else {
                ModuleSwitch(ModuleSwitchSize.Standard, checked = module.enabled, onClick = onModuleToggle, label = module.title)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = module.durationMinutes.toEditorDurationText(),
                color = colors.textPrimary,
                style = typography.body,
            )
        }
        HorizontalDivider(color = colors.divider)
        Column(
            modifier = Modifier.alpha(behaviourAreaAlpha),
            verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
        ) {
            module.behaviors.forEach { behavior ->
                Column {
                    BehaviorInformationRow(
                        label = behavior.label,
                        value = behavior.value,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = if (module.enabled) behavior.key?.let { key -> { onBehaviourClick(key) } } else null,
                        options = if (module.enabled) behavior.key?.let(::behaviourOptions).orEmpty() else emptyList(),
                        onOptionSelected = if (module.enabled) {
                            behavior.key?.let { key -> { option -> onBehaviourSelected(key, option) } }
                        } else null,
                    )
                    behavior.supportingText?.let { supportingText ->
                        Text(
                            text = stringResource(supportingText),
                            color = colors.textSecondary,
                            style = typography.smallRegular.copy(fontSize = 10.sp, lineHeight = 12.sp),
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.alpha(behaviourAreaAlpha)) {
            module.targets.forEach { target ->
                TargetStepper(
                    label = stringResource(target.label),
                    value = target.value,
                    enabled = module.enabled && module.behaviors.firstOrNull {
                        it.key == if (target.label == R.string.template_squat_target) BehaviourKey.SQUATS else BehaviourKey.WALKING
                    }?.value?.startsWith("On") == true,
                    onDecrement = { module.behaviors.firstOrNull { it.key == if (target.label == R.string.template_squat_target) BehaviourKey.SQUATS else BehaviourKey.WALKING }?.key?.let { onTargetChange(it, -1) } },
                    onIncrement = { module.behaviors.firstOrNull { it.key == if (target.label == R.string.template_squat_target) BehaviourKey.SQUATS else BehaviourKey.WALKING }?.key?.let { onTargetChange(it, 1) } },
                )
                if (target.label == R.string.template_squat_target) {
                    squatTargetError?.let { error -> Text(text = error, color = colors.warning, style = typography.smallRegular) }
                } else {
                    walkingTargetError?.let { error -> Text(text = error, color = colors.warning, style = typography.smallRegular) }
                }
            }
        }
    }
}

@Composable
private fun TemplateEditorModuleCard(
    title: String,
    duration: String,
    behaviors: List<TemplateBehavior>,
    moduleEnabled: Boolean,
    onModuleToggle: (() -> Unit)?,
    onBehaviorClick: ((String) -> Unit)?,
    onBehaviorOptionSelected: ((String, String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    val behaviourAreaAlpha = if (moduleEnabled) 1f else 0.45f

    TemplateCardSurface(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space4)) {
                Text(text = title, color = colors.textPrimary, style = typography.sectionHeading)
                Text(
                    text = stringResource(if (moduleEnabled) R.string.template_module_on else R.string.template_module_off),
                    color = colors.textSecondary,
                    style = typography.smallRegular,
                )
            }
            ModuleSwitch(ModuleSwitchSize.Standard, checked = moduleEnabled, onClick = onModuleToggle, label = title)
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = duration, color = colors.textPrimary, style = typography.body)
        }
        HorizontalDivider(color = colors.divider)
        Column(
            modifier = Modifier.alpha(behaviourAreaAlpha),
            verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
        ) {
            behaviors.forEach { behavior ->
                BehaviorInformationRow(
                    label = behavior.label,
                    value = behavior.value,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (moduleEnabled) onBehaviorClick?.let { callback -> { callback(behavior.label) } } else null,
                    options = if (moduleEnabled) behavior.options else emptyList(),
                    onOptionSelected = if (moduleEnabled) {
                        onBehaviorOptionSelected?.let { callback -> { option -> callback(behavior.label, option) } }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun TargetStepper(
    label: String,
    value: Int,
    enabled: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val colors = MorningFlowDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = colors.textPrimary, style = MorningFlowDesignSystem.typography.body)
        Row(
            horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TargetButton(stringResource(R.string.template_decrement), stringResource(R.string.a11y_decrease, label), onDecrement, enabled)
            Text(
                text = value.toString(),
                modifier = Modifier.width(20.dp),
                color = colors.textPrimary,
                style = MorningFlowDesignSystem.typography.smallStrong,
            )
            TargetButton(stringResource(R.string.template_increment), stringResource(R.string.a11y_increase, label), onIncrement, enabled)
        }
    }
}

@Composable
private fun TargetButton(label: String, description: String, onClick: () -> Unit, enabled: Boolean) {
    val colors = MorningFlowDesignSystem.colors
    Box(
        modifier = Modifier
            .size(28.dp)
            .border(1.dp, colors.borderDefault, MorningFlowShapes.Radius14)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = colors.textPrimary, style = MorningFlowDesignSystem.typography.body)
    }
}

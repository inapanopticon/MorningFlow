package com.morningflow.app.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.common.PrimaryButton
import com.morningflow.app.core.designsystem.component.common.SecondaryButton
import com.morningflow.app.core.designsystem.component.navigation.EditorialArrowButton
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowBottomNavigation
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowNavigationDestination
import com.morningflow.app.core.designsystem.icon.MorningFlowIcons
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.model.Appearance
import com.morningflow.app.core.model.ReminderPreferences
import com.morningflow.app.feature.settings.component.SettingsSection
import com.morningflow.app.feature.settings.component.SettingsToggleRow
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    data: SettingsDisplayData,
    onBack: () -> Unit,
    onToggle: (SettingsToggleKey) -> Unit,
    onAddThing: (String) -> Unit = {},
    onRemoveThing: (String) -> Unit = {},
    weatherCity: String? = null,
    onAppearanceChange: (Appearance) -> Unit = {},
    onWeatherCityClick: () -> Unit = {},
    onVolumeChanged: (SettingsToggleKey, Float) -> Unit,
    onPreferenceSelected: (SettingsPreferenceKey, Int?) -> Unit,
    onScheduleTimeClick: (SettingsTimeField, java.time.LocalTime) -> Unit = { _, _ -> },
    onScheduleSave: () -> Unit = {},
    onScheduleCancel: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
    onPermissionClick: (SettingsPermissionKey) -> Unit,
    onNavigationSelected: (MorningFlowNavigationDestination) -> Unit,
    openPermissions: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val scrollState = rememberScrollState()
    var scrollViewportTop by remember { mutableStateOf<Int?>(null) }
    var scrollViewportHeight by remember { mutableStateOf(0) }
    var permissionsTitleContentTop by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(
        openPermissions,
        permissionsTitleContentTop,
        scrollViewportTop,
        scrollViewportHeight,
        scrollState.maxValue,
    ) {
        val titleTop = permissionsTitleContentTop ?: return@LaunchedEffect
        val viewportTop = scrollViewportTop ?: return@LaunchedEffect
        if (openPermissions && scrollViewportHeight > 0) {
            val desiredViewportOffset = (scrollViewportHeight * 0.20f).roundToInt()
            val targetScroll = titleTop - viewportTop - desiredViewportOffset
            scrollState.animateScrollTo(targetScroll.coerceIn(0, scrollState.maxValue))
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    scrollViewportTop = position.y.roundToInt()
                    scrollViewportHeight = coordinates.size.height
                }
                .verticalScroll(scrollState)
                .padding(horizontal = MorningFlowSpacing.Space32),
        ) {
            Spacer(Modifier.height(MorningFlowSpacing.Space32))
            SettingsHeader()

            Spacer(Modifier.height(MorningFlowSpacing.Space32))
            SettingsSection(
                title = stringResource(R.string.settings_general),
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
            SettingsTimeRow(
                label = stringResource(R.string.settings_earliest_wake_up),
                value = data.earliestWakeUp.toSettingsTimeText(),
                errorMessage = if (SettingsTimeField.EarliestWakeUp in data.scheduleErrors) {
                    stringResource(R.string.settings_earliest_before_latest_error)
                } else null,
                onClick = if (data.isScheduleReady) {
                    { onScheduleTimeClick(SettingsTimeField.EarliestWakeUp, data.earliestWakeUp) }
                } else null,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space12))
            SettingsTimeRow(
                label = stringResource(R.string.settings_latest_wake_up),
                value = data.latestWakeUp.toSettingsTimeText(),
                errorMessage = if (SettingsTimeField.LatestWakeUp in data.scheduleErrors) {
                    stringResource(R.string.settings_latest_schedule_error)
                } else null,
                onClick = if (data.isScheduleReady) {
                    { onScheduleTimeClick(SettingsTimeField.LatestWakeUp, data.latestWakeUp) }
                } else null,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space12))
            SettingsTimeRow(
                label = stringResource(R.string.settings_ready_by),
                value = data.readyBy.toSettingsTimeText(),
                errorMessage = if (SettingsTimeField.ReadyBy in data.scheduleErrors) {
                    stringResource(R.string.settings_ready_by_after_latest_error)
                } else null,
                onClick = if (data.isScheduleReady) {
                    { onScheduleTimeClick(SettingsTimeField.ReadyBy, data.readyBy) }
                } else null,
            )
            if (data.isEditingSchedule) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SecondaryButton(
                        label = stringResource(R.string.settings_cancel),
                        onClick = onScheduleCancel,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        label = stringResource(R.string.settings_save),
                        onClick = onScheduleSave,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (data.scheduleError == SettingsScheduleError.SaveFailed) {
                    Text(
                        text = stringResource(R.string.settings_schedule_save_failed),
                        color = colors.warning,
                        style = MorningFlowDesignSystem.typography.fieldError,
                    )
                }
            }
            if (data.scheduleError == SettingsScheduleError.ReadinessFailed) {
                Text(
                    text = stringResource(R.string.settings_schedule_load_failed),
                    color = colors.warning,
                    style = MorningFlowDesignSystem.typography.fieldError,
                )
            }

            data.schedulerWarning?.let { warning ->
                Spacer(Modifier.height(MorningFlowSpacing.Space16))
                SettingsSchedulerWarningCard(
                    warning = warning,
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                )
            }

            Spacer(Modifier.height(MorningFlowSpacing.Space32))
            SettingsSection(title = stringResource(R.string.settings_appearance))
            Spacer(Modifier.height(MorningFlowSpacing.Space12))
            AppearanceChoice(selected = data.appearance, onSelect = onAppearanceChange)

            Spacer(Modifier.height(MorningFlowSpacing.Space32))
            SettingsSection(
                title = stringResource(R.string.settings_audio),
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space4))
            data.audioSettings.forEachIndexed { index, item ->
                SettingsToggleRow(
                    label = stringResource(item.label),
                    description = item.description?.let { stringResource(it) },
                    enabled = item.enabled,
                    volume = item.volume,
                    onClick = if (item.isInteractive) {
                        { onToggle(item.key) }
                    } else {
                        null
                    },
                    onVolumeChanged = if (item.volume != null) {
                        { value -> onVolumeChanged(item.key, value) }
                    } else {
                        null
                    },
                )
                if (index != data.audioSettings.lastIndex) {
                    Spacer(Modifier.height(if (index == 0) 17.dp else 11.dp))
                }
            }

            Column {
                Spacer(Modifier.height(MorningFlowSpacing.Space32))
                SettingsSection(
                    title = stringResource(R.string.settings_permissions),
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInRoot()
                        permissionsTitleContentTop =
                            position.y.roundToInt() + scrollState.value
                    },
                )
                Spacer(Modifier.height(MorningFlowSpacing.Space6))
                SettingsPermissions(data.permissions, onPermissionClick)
            }

            Spacer(Modifier.height(MorningFlowSpacing.Space32))
            SettingsSection(
                title = stringResource(R.string.settings_reminder_preferences),
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space6))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable(onClick = onWeatherCityClick),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_city_label),
                    color = colors.textPrimary,
                    style = MorningFlowDesignSystem.typography.fieldValue,
                )
                Text(
                    text = weatherCity ?: stringResource(R.string.settings_city_device),
                    color = colors.brandPrimary,
                    style = MorningFlowDesignSystem.typography.fieldValue,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space12)) {
                data.reminderPreferences.forEach { item ->
                    SettingsValueRow(
                        label = stringResource(item.label),
                        value = item.preferenceValue(),
                        valueActive = item.active,
                        preferenceKey = item.key,
                        onClick = if (item.key == SettingsPreferenceKey.Sunscreen) {
                            { onPreferenceSelected(item.key, null) }
                        } else {
                            null
                        },
                        menuValues = item.key.preferenceValues(),
                        selectedValue = item.value,
                        onMenuValueSelected = { selected ->
                            onPreferenceSelected(item.key, selected)
                        },
                    )
                }
            }

            Spacer(Modifier.height(MorningFlowSpacing.Space32))
            SettingsSection(
                title = stringResource(R.string.settings_things_to_bring),
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space6))
            ThingsToBringEditor(
                items = data.thingsToBring,
                onAdd = onAddThing,
                onRemove = onRemoveThing,
            )
            Spacer(Modifier.height(MorningFlowSpacing.Space24))
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = MorningFlowSpacing.Space8, bottom = MorningFlowSpacing.Space12),
            contentAlignment = Alignment.Center,
        ) {
            MorningFlowBottomNavigation(
                selected = data.selectedNavigation,
                onSelected = onNavigationSelected,
            )
        }
    }
}
@Composable
private fun SettingsSchedulerWarningCard(
    warning: SettingsSchedulerWarning,
    onOpenExactAlarmSettings: () -> Unit,
) {
    val colors = MorningFlowDesignSystem.colors
    val title = when (warning) {
        SettingsSchedulerWarning.ExactAccessRequired ->
            stringResource(R.string.settings_scheduler_exact_alarm_title)
        SettingsSchedulerWarning.PlatformRejected ->
            stringResource(R.string.settings_scheduler_platform_rejected_title)
    }
    val description = when (warning) {
        SettingsSchedulerWarning.ExactAccessRequired ->
            stringResource(R.string.settings_scheduler_exact_alarm_description)
        SettingsSchedulerWarning.PlatformRejected ->
            stringResource(R.string.settings_scheduler_platform_rejected_description)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated, RoundedCornerShape(8.dp))
            .padding(MorningFlowSpacing.Space16),
    ) {
        Text(
            text = title,
            color = colors.warning,
            style = MorningFlowDesignSystem.typography.moduleTitle,
        )
        Spacer(Modifier.height(MorningFlowSpacing.Space4))
        Text(
            text = description,
            color = colors.textSecondary,
            style = MorningFlowDesignSystem.typography.body,
        )
        if (warning.hasOpenSettingsAction) {
            Spacer(Modifier.height(MorningFlowSpacing.Space12))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                EditorialArrowButton(
                    label = stringResource(R.string.settings_scheduler_open_settings),
                    onClick = onOpenExactAlarmSettings,
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    val colors = MorningFlowDesignSystem.colors
    // Same header as Templates: Settings is a tab, so Back and the bottom bar lead out of it.
    Text(
        text = stringResource(R.string.app_name),
        color = colors.textPrimary,
        style = MorningFlowDesignSystem.typography.brandLabel,
    )
    Spacer(Modifier.height(MorningFlowSpacing.Space24))
    Text(
        text = stringResource(R.string.settings_title),
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        maxLines = 1,
        softWrap = false,
        color = colors.brandPrimary,
        style = MorningFlowDesignSystem.typography.pageTitle,
    )
}

@Composable
private fun SettingsTimeRow(
    label: String,
    value: String,
    errorMessage: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column {
    SettingsTextRow(
        label = label,
        value = value,
        valueColor = MorningFlowDesignSystem.colors.textPrimary,
        valueStyle = MorningFlowDesignSystem.typography.fieldValue,
        onClick = onClick,
    )
        errorMessage?.let {
            Text(
                text = it,
                color = MorningFlowDesignSystem.colors.warning,
                style = MorningFlowDesignSystem.typography.fieldError,
            )
        }
    }
}

@Composable
private fun SettingsTextRow(
    label: String,
    value: String,
    valueColor: Color,
    valueStyle: TextStyle,
    onClick: (() -> Unit)? = null,
) {
    val colors = MorningFlowDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(33.dp)
            .let { modifier -> onClick?.let { modifier.clickable(onClick = it) } ?: modifier },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = colors.textPrimary, style = MorningFlowDesignSystem.typography.fieldValue)
        Text(text = value, color = valueColor, style = valueStyle)
    }
}

@Composable
private fun SettingsPermissions(
    items: List<SettingsPermissionDisplayData>,
    onPermissionClick: (SettingsPermissionKey) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            SettingsPermissionRow(item, onPermissionClick)
            if (index != items.lastIndex) {
                Spacer(Modifier.height(if (item.supportingText == null) MorningFlowSpacing.Space12 else MorningFlowSpacing.Space6))
            }
        }
    }
}

@Composable
private fun SettingsPermissionRow(
    item: SettingsPermissionDisplayData,
    onPermissionClick: (SettingsPermissionKey) -> Unit,
) {
    val colors = MorningFlowDesignSystem.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(33.dp)
                .clickable(
                    enabled = item.isClickable(),
                ) {
                    onPermissionClick(item.key)
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(item.label),
                color = colors.textPrimary,
                style = MorningFlowDesignSystem.typography.fieldValue,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        when (item.status) {
                            SettingsPermissionStatus.Granted -> R.string.settings_granted
                            SettingsPermissionStatus.Denied -> R.string.settings_denied
                            SettingsPermissionStatus.NotAvailable -> R.string.settings_unavailable
                        },
                    ),
                    color = when (item.status) {
                        SettingsPermissionStatus.Granted -> colors.textPrimary
                        // Tapping the row opens the place to grant it, so this reads as an action.
                        SettingsPermissionStatus.Denied -> colors.brandPrimary
                        SettingsPermissionStatus.NotAvailable -> colors.textSecondary
                    },
                    style = MorningFlowDesignSystem.typography.fieldValue.copy(lineHeight = 16.sp),
                )
            }
        }
        item.supportingText?.let {
            Text(
                text = stringResource(it),
                modifier = Modifier.fillMaxWidth(),
                color = colors.textSecondary,
                style = MorningFlowDesignSystem.typography.body.copy(
                    lineHeight = 15.sp,
                    letterSpacing = (-0.18).sp,
                ),
            )
        }
    }
}

@Composable
private fun SettingsValueRow(
    label: String,
    value: String,
    valueActive: Boolean,
    preferenceKey: SettingsPreferenceKey = SettingsPreferenceKey.Unknown,
    onClick: (() -> Unit)? = null,
    menuValues: List<Int?> = emptyList(),
    selectedValue: Int? = null,
    onMenuValueSelected: (Int?) -> Unit = {},
) {
    val colors = MorningFlowDesignSystem.colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(33.dp)
                .let {
                    when {
                        onClick != null -> it.clickable(onClick = onClick)
                        else -> it
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, color = colors.textPrimary, style = MorningFlowDesignSystem.typography.fieldValue)
            Box {
                Text(
                    text = value,
                    color = if (valueActive) colors.textPrimary else colors.textSecondary,
                    style = MorningFlowDesignSystem.typography.fieldValue.copy(lineHeight = 16.sp),
                    modifier = if (menuValues.isNotEmpty()) {
                        Modifier.clickable { expanded = true }
                    } else {
                        Modifier
                    },
                )
                if (menuValues.isNotEmpty()) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        menuValues.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(text = preferenceOptionText(preferenceKey, option)) },
                                trailingIcon = {
                                    RadioButton(
                                        selected = option == selectedValue,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    expanded = false
                                    onMenuValueSelected(option)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPreferenceDisplayData.preferenceValue(): String = when (key) {
    SettingsPreferenceKey.Umbrella -> value?.let {
        stringResource(R.string.settings_rain_threshold_format, it)
    } ?: stringResource(R.string.settings_off)
    SettingsPreferenceKey.Jacket -> value?.let {
        stringResource(R.string.settings_temperature_threshold_format, it)
    } ?: stringResource(R.string.settings_off)
    SettingsPreferenceKey.Sunscreen -> stringResource(if (active) R.string.settings_on else R.string.settings_off)
    SettingsPreferenceKey.Unknown -> stringResource(R.string.settings_unavailable)
}

private fun SettingsPreferenceKey.preferenceValues(): List<Int?> = when (this) {
    SettingsPreferenceKey.Umbrella -> listOf<Int?>(null) + ReminderPreferences.UMBRELLA_RAIN_PERCENT_OPTIONS
    SettingsPreferenceKey.Jacket -> listOf<Int?>(null) + ReminderPreferences.JACKET_TEMPERATURE_OPTIONS
    else -> emptyList()
}

@Composable
private fun preferenceOptionText(itemKey: SettingsPreferenceKey, value: Int?): String = when {
    value == null -> stringResource(R.string.settings_off)
    itemKey == SettingsPreferenceKey.Umbrella ->
        stringResource(R.string.settings_rain_threshold_format, value)
    else -> stringResource(R.string.settings_temperature_threshold_format, value)
}

/** The packing list: each item can be removed, and new ones typed in below. */
@Composable
private fun ThingsToBringEditor(
    items: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = MorningFlowDesignSystem.colors
    var draft by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (draft.isNotBlank()) {
            onAdd(draft)
            draft = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space4)) {
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item,
                    modifier = Modifier.weight(1f),
                    color = colors.textPrimary,
                    style = MorningFlowDesignSystem.typography.fieldValue,
                )
                IconButton(onClick = { onRemove(item) }) {
                    Icon(
                        painter = painterResource(MorningFlowIcons.Close),
                        contentDescription = stringResource(R.string.settings_things_remove, item),
                        modifier = Modifier.size(18.dp),
                        tint = colors.textSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(MorningFlowSpacing.Space8))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(MAX_THING_LENGTH) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.settings_things_add_hint)) },
                singleLine = true,
                shape = MorningFlowShapes.Radius12,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.brandPrimary,
                    unfocusedBorderColor = colors.borderInput,
                    cursorColor = colors.brandPrimary,
                ),
            )
            Button(
                onClick = submit,
                enabled = draft.isNotBlank(),
                shape = MorningFlowShapes.Full,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandPrimary,
                    contentColor = colors.surface,
                ),
            ) {
                Text(stringResource(R.string.settings_things_add), style = MorningFlowDesignSystem.typography.buttonLabel)
            }
        }
    }
}

/** Follow the phone, or always light, or always dark. */
@Composable
private fun AppearanceChoice(selected: Appearance, onSelect: (Appearance) -> Unit) {
    val colors = MorningFlowDesignSystem.colors
    val labels = mapOf(
        Appearance.SYSTEM to R.string.settings_appearance_system,
        Appearance.LIGHT to R.string.settings_appearance_light,
        Appearance.DARK to R.string.settings_appearance_dark,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorningFlowShapes.Full)
            .border(1.dp, colors.borderInput, MorningFlowShapes.Full)
            .padding(MorningFlowSpacing.Space4),
        horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space4),
    ) {
        Appearance.entries.forEach { option ->
            val isSelected = option == selected
            Text(
                text = stringResource(labels.getValue(option)),
                modifier = Modifier
                    .weight(1f)
                    .clip(MorningFlowShapes.Full)
                    .background(if (isSelected) colors.brandPrimary else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(vertical = MorningFlowSpacing.Space10),
                color = if (isSelected) colors.surface else colors.textPrimary,
                style = MorningFlowDesignSystem.typography.buttonLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}


package com.morningflow.app.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningflow.app.app.MorningFlowApplication
import com.morningflow.app.core.data.calendar.api.CalendarPermissionSpec
import com.morningflow.app.core.data.location.api.LocationPermissionSpec
import com.morningflow.app.core.data.notification.api.NotificationAvailability
import com.morningflow.app.core.data.notification.api.NotificationPermissionSpec
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowNavigationDestination
import com.morningflow.app.core.designsystem.time.MorningFlowTimePickerDialog
import com.morningflow.app.core.permissions.AndroidPermissionChecker
import com.morningflow.app.core.permissions.createExactAlarmSettingsIntent
import com.morningflow.app.core.permissions.launchExactAlarmSettings
import com.morningflow.app.core.sensor.SensorPermissionSpec
import com.morningflow.app.feature.settings.component.CityPickerDialog
import kotlinx.coroutines.launch
import java.time.LocalTime

internal fun createAppDetailsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }

internal fun createAppNotificationSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }

internal fun settingsNotificationRequestAction(
    availability: NotificationAvailability,
    onRuntimePermissionRequest: () -> Unit,
    onAppNotificationSettings: () -> Unit,
    onChannelNotificationSettings: () -> Unit,
): (() -> Unit)? = when (availability) {
    NotificationAvailability.AVAILABLE -> onAppNotificationSettings
    NotificationAvailability.RUNTIME_PERMISSION_DENIED -> onRuntimePermissionRequest
    NotificationAvailability.APP_NOTIFICATIONS_DISABLED -> onAppNotificationSettings
    NotificationAvailability.CHANNEL_DISABLED -> onChannelNotificationSettings
}

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigationSelected: (MorningFlowNavigationDestination) -> Unit,
    openPermissions: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val data by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionChecker = remember(context) { AndroidPermissionChecker(context) }
    val application = context.applicationContext as MorningFlowApplication
    val exactAlarmAccessChecker = remember(application) {
        application.appContainer.exactAlarmAccessChecker
    }
    val notificationAvailability = remember(application) {
        application.appContainer.notificationAvailability
    }
    val notificationSettingsIntentProvider = remember(application) {
        application.appContainer.morningPromptNotificationSettingsIntentProvider
    }

    var pickerRequest by remember { mutableStateOf<Pair<SettingsTimeField, LocalTime>?>(null) }
    pickerRequest?.let { (field, selected) ->
        MorningFlowTimePickerDialog(
            initial = selected,
            onConfirm = { time ->
                pickerRequest = null
                viewModel.updateScheduleTime(field, time)
            },
            onDismiss = { pickerRequest = null },
        )
    }

    fun openScheduleTimePicker(field: SettingsTimeField, value: LocalTime) {
        viewModel.beginScheduleEdit { schedule ->
            val selected = when (field) {
                SettingsTimeField.EarliestWakeUp -> schedule.earliestWakeUp
                SettingsTimeField.LatestWakeUp -> schedule.latestWakeUp
                SettingsTimeField.ReadyBy -> schedule.readyBy
            }
            pickerRequest = field to selected
        }
    }

    fun refreshPermissionState() {
        viewModel.refreshPermissionState(
            locationGranted = permissionChecker.hasAll(LocationPermissionSpec.requiredPermissions),
            calendarGranted = permissionChecker.hasAll(CalendarPermissionSpec.requiredPermissions),
            sensorsGranted = permissionChecker.hasAll(SensorPermissionSpec.requiredPermissions),
            notificationAvailability = notificationAvailability.current(),
            exactAlarmGranted = exactAlarmAccessChecker.hasExactAlarmAccess(),
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshPermissionState()
    }

    fun openExactAlarmSettings() {
        launchExactAlarmSettings(
            createExactAlarmSettingsIntent(context.packageName),
            context::startActivity,
        )
    }

    fun openAppDetails(key: SettingsPermissionKey) {
        if (key == SettingsPermissionKey.AlarmsAndReminders) {
            openExactAlarmSettings()
            return
        }
        val intent = when (key) {
            SettingsPermissionKey.Location,
            SettingsPermissionKey.Calendar,
            SettingsPermissionKey.Sensors,
            -> createAppDetailsIntent(context.packageName)
            SettingsPermissionKey.Notifications -> {
                settingsNotificationRequestAction(
                    availability = notificationAvailability.current(),
                    onRuntimePermissionRequest = {
                        val permissions = NotificationPermissionSpec.requiredPermissionsFor(Build.VERSION.SDK_INT)
                        if (permissions.isEmpty()) {
                            refreshPermissionState()
                        } else {
                            notificationLauncher.launch(permissions.toTypedArray())
                        }
                    },
                    onAppNotificationSettings = {
                        context.startActivity(createAppNotificationSettingsIntent(context.packageName))
                    },
                    onChannelNotificationSettings = {
                        context.startActivity(
                            notificationSettingsIntentProvider.createChannelSettingsIntent(),
                        )
                    },
                )?.invoke()
                return
            }
            SettingsPermissionKey.AlarmsAndReminders -> return
            SettingsPermissionKey.Unknown -> return
        }
        context.startActivity(intent)
    }

    LaunchedEffect(permissionChecker, exactAlarmAccessChecker, notificationAvailability) {
        refreshPermissionState()
    }

    DisposableEffect(lifecycleOwner, permissionChecker, exactAlarmAccessChecker, notificationAvailability) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val manualCities = application.appContainer.manualCityRepository
    val weatherCity by manualCities.city.collectAsStateWithLifecycle()
    val cityScope = rememberCoroutineScope()
    var showCityPicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(manualCities) { manualCities.load() }
    // Re-reads location and weather so Home shows the new place straight away.
    fun applyCity(change: suspend () -> Unit) {
        showCityPicker = false
        cityScope.launch {
            change()
            application.appContainer.weatherLocationCoordinator.refresh()
        }
    }
    if (showCityPicker) {
        CityPickerDialog(
            hasManualCity = weatherCity != null,
            search = manualCities::search,
            onChoose = { city -> applyCity { manualCities.choose(city) } },
            onUseDeviceLocation = { applyCity { manualCities.clear() } },
            onDismiss = { showCityPicker = false },
        )
    }

    // A pending schedule edit is cancelled first, so back never discards it unseen.
    BackHandler(enabled = data.isEditingSchedule) {
        viewModel.cancelScheduleEdit()
    }

    SettingsScreen(
        data = data,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onAddThing = viewModel::addThingToBring,
        onRemoveThing = viewModel::removeThingToBring,
        onAppearanceChange = viewModel::setAppearance,
        weatherCity = weatherCity?.displayLabel,
        onWeatherCityClick = { showCityPicker = true },
        onVolumeChanged = viewModel::setVolume,
        onPreferenceSelected = viewModel::selectReminder,
        onScheduleTimeClick = ::openScheduleTimePicker,
        onScheduleSave = viewModel::saveSchedule,
        onScheduleCancel = viewModel::cancelScheduleEdit,
        onOpenExactAlarmSettings = ::openExactAlarmSettings,
        onPermissionClick = ::openAppDetails,
        onNavigationSelected = onNavigationSelected,
        openPermissions = openPermissions,
    )
}

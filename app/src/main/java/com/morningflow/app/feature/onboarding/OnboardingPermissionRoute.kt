package com.morningflow.app.feature.onboarding

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.morningflow.app.core.permissions.AndroidPermissionChecker
import com.morningflow.app.core.permissions.createExactAlarmSettingsIntent
import com.morningflow.app.core.permissions.launchExactAlarmSettings
import com.morningflow.app.core.sensor.SensorPermissionSpec

internal fun createOnboardingAppNotificationSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }

internal fun onboardingNotificationRequestAction(
    availability: NotificationAvailability,
    onRuntimePermissionRequest: () -> Unit,
    onAppNotificationSettings: () -> Unit,
    onChannelNotificationSettings: () -> Unit,
): (() -> Unit)? = when (availability) {
    NotificationAvailability.AVAILABLE -> null
    NotificationAvailability.RUNTIME_PERMISSION_DENIED -> onRuntimePermissionRequest
    NotificationAvailability.APP_NOTIFICATIONS_DISABLED -> onAppNotificationSettings
    NotificationAvailability.CHANNEL_DISABLED -> onChannelNotificationSettings
}

/**
 * Connects the onboarding permission UI to Android's runtime permission API.
 *
 * Permission requests are started only by an explicit row click. Lifecycle resume and
 * launcher callbacks only re-read the actual platform state, so recomposition cannot
 * repeatedly open a system dialog.
 */
@Composable
fun OnboardingPermissionRoute(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
    var pendingRequest by remember { mutableStateOf<PermissionRequest?>(null) }

    fun refreshPermissionState() {
        viewModel.refreshPermissionState(
            locationGranted = permissionChecker.hasAll(LocationPermissionSpec.requiredPermissions),
            calendarGranted = permissionChecker.hasAll(CalendarPermissionSpec.requiredPermissions),
            sensorsGranted = permissionChecker.hasAll(SensorPermissionSpec.requiredPermissions),
            notificationAvailability = notificationAvailability.current(),
            exactAlarmGranted = exactAlarmAccessChecker.hasExactAlarmAccess(),
        )
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        pendingRequest = null
        refreshPermissionState()
    }
    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        pendingRequest = null
        refreshPermissionState()
    }
    val sensorsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        pendingRequest = null
        refreshPermissionState()
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        pendingRequest = null
        refreshPermissionState()
    }

    LaunchedEffect(permissionChecker, exactAlarmAccessChecker, notificationAvailability) {
        refreshPermissionState()
    }

    DisposableEffect(lifecycleOwner, permissionChecker, exactAlarmAccessChecker, notificationAvailability) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingRequest == null) {
                refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PermissionScreen(
        data = PermissionDisplayData(
            location = uiState.locationPermission,
            calendar = uiState.calendarPermission,
            sensors = uiState.sensorsPermission,
            notifications = uiState.notificationPermission,
            exactAlarm = uiState.exactAlarmPermission,
        ),
        onNext = onNext,
        onLocationRequest = if (uiState.locationPermission != PermissionAccess.Granted) {
            {
                if (pendingRequest == null) {
                    pendingRequest = PermissionRequest.LOCATION
                    locationLauncher.launch(LocationPermissionSpec.requiredPermissions.toTypedArray())
                }
            }
        } else {
            null
        },
        onCalendarRequest = if (
            uiState.locationPermission != PermissionAccess.NotAvailable &&
                uiState.calendarPermission != PermissionAccess.Granted
        ) {
            {
                if (pendingRequest == null) {
                    pendingRequest = PermissionRequest.CALENDAR
                    calendarLauncher.launch(CalendarPermissionSpec.requiredPermissions.toTypedArray())
                }
            }
        } else {
            null
        },
        onSensorsRequest = if (uiState.sensorsPermission != PermissionAccess.Granted) {
            {
                if (pendingRequest == null) {
                    val permissions = SensorPermissionSpec.requiredPermissions
                    if (permissions.isEmpty()) {
                        refreshPermissionState()
                    } else {
                        pendingRequest = PermissionRequest.SENSORS
                        sensorsLauncher.launch(permissions.toTypedArray())
                    }
                }
            }
        } else {
            null
        },
        onNotificationsRequest = if (uiState.notificationPermission == PermissionAccess.Denied) {
            {
                onboardingNotificationRequestAction(
                    availability = notificationAvailability.current(),
                    onRuntimePermissionRequest = {
                        if (pendingRequest == null) {
                            val permissions = NotificationPermissionSpec.requiredPermissionsFor(Build.VERSION.SDK_INT)
                            if (permissions.isEmpty()) {
                                refreshPermissionState()
                            } else {
                                pendingRequest = PermissionRequest.NOTIFICATIONS
                                notificationLauncher.launch(permissions.toTypedArray())
                            }
                        }
                    },
                    onAppNotificationSettings = {
                        context.startActivity(
                            createOnboardingAppNotificationSettingsIntent(context.packageName),
                        )
                    },
                    onChannelNotificationSettings = {
                        context.startActivity(
                            notificationSettingsIntentProvider.createChannelSettingsIntent(),
                        )
                    },
                )?.invoke()
            }
        } else {
            null
        },
        onExactAlarmRequest = if (uiState.exactAlarmPermission == PermissionAccess.Denied) {
            {
                launchExactAlarmSettings(
                    createExactAlarmSettingsIntent(context.packageName),
                    context::startActivity,
                )
            }
        } else {
            null
        },
        modifier = modifier,
    )
}

private enum class PermissionRequest {
    LOCATION,
    CALENDAR,
    SENSORS,
    NOTIFICATIONS,
}

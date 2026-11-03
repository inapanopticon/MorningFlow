package com.morningflow.app.app

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowNavigationDestination
import com.morningflow.app.core.designsystem.illustration.MorningFlowIllustrations
import com.morningflow.app.core.designsystem.theme.MorningFlowTheme
import com.morningflow.app.core.model.Appearance
import com.morningflow.app.core.model.OnboardingStep
import com.morningflow.app.core.model.SessionStatus
import com.morningflow.app.feature.completion.CompletionDisplayData
import com.morningflow.app.feature.completion.CompletionScreen
import com.morningflow.app.feature.history.HistoryScreen
import com.morningflow.app.feature.history.HistoryViewModel
import com.morningflow.app.feature.home.HomeScreen
import com.morningflow.app.feature.home.HomeViewModel
import com.morningflow.app.feature.onboarding.ColdStartDisplayData
import com.morningflow.app.feature.onboarding.ColdStartScreen
import com.morningflow.app.feature.onboarding.OnboardingFormDisplayData
import com.morningflow.app.feature.onboarding.OnboardingFormScreen
import com.morningflow.app.feature.onboarding.OnboardingFormVisualState
import com.morningflow.app.feature.onboarding.OnboardingPermissionRoute
import com.morningflow.app.feature.onboarding.OnboardingUiState
import com.morningflow.app.feature.onboarding.OnboardingViewModel
import com.morningflow.app.feature.session.MorningSessionScreen
import com.morningflow.app.feature.session.SessionDisplayMapper
import com.morningflow.app.feature.settings.SettingsRoute
import com.morningflow.app.feature.settings.SettingsViewModel
import com.morningflow.app.feature.templates.TemplatesRoute
import com.morningflow.app.feature.templates.TemplatesViewModel
import java.time.LocalTime
import kotlin.math.max

private enum class AppPage { Home, Templates, History, Settings, SettingsPermissions }

/** What fills the window; the cross-fade between screens is keyed on it. */
private sealed interface AppScreen {
    data class Onboarding(val step: OnboardingStep) : AppScreen
    data object Session : AppScreen
    data object Completion : AppScreen
    data class Tab(val page: AppPage) : AppScreen
}

/**
 * Shows whichever screen the session's own status calls for, rather than keeping a
 * separate navigation state that could disagree with what the engine is doing.
 */
@Composable
fun MorningFlowApp(viewModel: MorningFlowViewModel = viewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val sessionRuntime by viewModel.sessionRuntime.collectAsStateWithLifecycle()
    val isExitDialogVisible by viewModel.isExitDialogVisible.collectAsStateWithLifecycle()
    val application = LocalContext.current.applicationContext as MorningFlowApplication
    val factory = remember(application) { AppFeatureViewModelFactory(application.appContainer) }
    val onboardingViewModel: OnboardingViewModel = viewModel(factory = factory)
    val onboardingState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
    val homeViewModel: HomeViewModel = viewModel(factory = factory)
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val templatesViewModel: TemplatesViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val historyViewModel: HistoryViewModel = viewModel(factory = factory)
    val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(AppPage.Home) }
    // Opening the tab again always starts on this month.
    LaunchedEffect(page) { if (page == AppPage.History) historyViewModel.showCurrentMonth() }

    LaunchedEffect(onboardingState.step, session.status, page) {
        if (onboardingState.step == OnboardingStep.COMPLETE &&
            page == AppPage.Home &&
            session.status in setOf(SessionStatus.NOT_STARTED, SessionStatus.EXITED)
        ) {
            homeViewModel.refresh()
        }
    }

    val settings by application.appContainer.settingsRepository.settings.collectAsStateWithLifecycle()
    val darkTheme = when (settings.appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    // The bars draw over the app, so their icons follow the app's choice, not the phone's.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MorningFlowTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val screen = when {
                onboardingState.step != OnboardingStep.COMPLETE -> AppScreen.Onboarding(onboardingState.step)
                session.status == SessionStatus.RUNNING || session.status == SessionStatus.PAUSED -> AppScreen.Session
                session.status == SessionStatus.COMPLETED -> AppScreen.Completion
                else -> AppScreen.Tab(page)
            }
            // Screens cross-fade rather than cut. Each screen is drawn from its own key, so the
            // outgoing one keeps showing what it was while it fades.
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(220, delayMillis = 60)).togetherWith(fadeOut(tween(160))) },
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                label = "screen",
            ) { target ->
                if (target is AppScreen.Onboarding) {
                    OnboardingContent(onboardingViewModel, onboardingState)
                } else {
                    when (target) {
                        AppScreen.Session -> {
                            // Back asks before ending, exactly like the close button, instead
                            // of finishing the activity and silently dropping the session.
                            BackHandler {
                                if (isExitDialogVisible) viewModel.dismissExitDialog() else viewModel.requestExit()
                            }
                            SessionDisplayMapper.map(session, isExitDialogVisible, sessionRuntime)?.let { data ->
                                MorningSessionScreen(
                                    data = data,
                                    onClose = viewModel::requestExit,
                                    onPlaybackClick = viewModel::togglePlayback,
                                    onComplete = viewModel::skipStage,
                                    onConfirmExit = viewModel::confirmExit,
                                    onDismissDialog = viewModel::dismissExitDialog,
                                )
                            }
                        }

                        AppScreen.Completion -> {
                            BackHandler {
                                page = AppPage.Home
                                viewModel.returnHome()
                            }
                            CompletionScreen(
                                data = CompletionDisplayData(
                                    illustration = MorningFlowIllustrations.MorningSessionComplete,
                                    // A session under a minute still reads as one rather than zero.
                                    actualDurationMinutes = max(1, session.elapsedSeconds / 60),
                                    finishedAt = LocalTime.now(),
                                ),
                                onHome = {
                                    page = AppPage.Home
                                    viewModel.returnHome()
                                },
                            )
                        }

                        is AppScreen.Tab -> {
                            // Tabs other than Home step back to Home; Home itself leaves the app.
                            // Templates registers its own handler for its inner pages.
                            BackHandler(enabled = target.page in setOf(AppPage.History, AppPage.Settings, AppPage.SettingsPermissions)) {
                                page = AppPage.Home
                            }
                            when (target.page) {
                                AppPage.Home -> HomeScreen(
                                    uiState = homeState,
                                    onStart = { templateId ->
                                        viewModel.startSession(templateId)
                                        page = AppPage.Home
                                    },
                                    onOpenTemplates = { page = AppPage.Templates },
                                    onOpenSettings = {
                                        page = AppPage.Settings
                                    },
                                    onOpenSettingsPermissions = {
                                        page = AppPage.SettingsPermissions
                                    },
                                    onNavigationSelected = { destination ->
                                        page = when (destination) {
                                            MorningFlowNavigationDestination.Home -> AppPage.Home
                                            MorningFlowNavigationDestination.Templates -> AppPage.Templates
                                            MorningFlowNavigationDestination.History -> AppPage.History
                                            MorningFlowNavigationDestination.Settings -> AppPage.Settings
                                        }
                                    },
                                    onRefreshTime = homeViewModel::refreshTime,
                                    onRefreshCalendar = homeViewModel::refreshCalendar,
                                )

                                AppPage.Templates -> TemplatesRoute(
                                    viewModel = templatesViewModel,
                                    onStart = { templateId ->
                                        if (viewModel.startSession(templateId)) {
                                            // Session status drives the screen; Home is the safe
                                            // destination after exit or completion.
                                            page = AppPage.Home
                                        }
                                    },
                                    onNavigationSelected = { destination ->
                                        page = when (destination) {
                                            MorningFlowNavigationDestination.Home -> AppPage.Home
                                            MorningFlowNavigationDestination.Templates -> AppPage.Templates
                                            MorningFlowNavigationDestination.History -> AppPage.History
                                            MorningFlowNavigationDestination.Settings -> AppPage.Settings
                                        }
                                    },
                                )

                                AppPage.History -> HistoryScreen(
                                    state = historyState,
                                    onPreviousMonth = historyViewModel::previousMonth,
                                    onNextMonth = historyViewModel::nextMonth,
                                    onNavigationSelected = { destination ->
                                        page = when (destination) {
                                            MorningFlowNavigationDestination.Home -> AppPage.Home
                                            MorningFlowNavigationDestination.Templates -> AppPage.Templates
                                            MorningFlowNavigationDestination.History -> AppPage.History
                                            MorningFlowNavigationDestination.Settings -> AppPage.Settings
                                        }
                                    },
                                )

                                AppPage.Settings -> SettingsRoute(
                                    viewModel = settingsViewModel,
                                    onBack = { page = AppPage.Home },
                                    onNavigationSelected = { destination ->
                                        page = when (destination) {
                                            MorningFlowNavigationDestination.Home -> AppPage.Home
                                            MorningFlowNavigationDestination.Templates -> AppPage.Templates
                                            MorningFlowNavigationDestination.History -> AppPage.History
                                            MorningFlowNavigationDestination.Settings -> AppPage.Settings
                                        }
                                    },
                                )

                                AppPage.SettingsPermissions -> SettingsRoute(
                                    viewModel = settingsViewModel,
                                    onBack = { page = AppPage.Home },
                                    onNavigationSelected = { destination ->
                                        page = when (destination) {
                                            MorningFlowNavigationDestination.Home -> AppPage.Home
                                            MorningFlowNavigationDestination.Templates -> AppPage.Templates
                                            MorningFlowNavigationDestination.History -> AppPage.History
                                            MorningFlowNavigationDestination.Settings -> AppPage.Settings
                                        }
                                    },
                                    openPermissions = true,
                                )
                            }
                        }

                        is AppScreen.Onboarding -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingContent(
    viewModel: OnboardingViewModel,
    state: OnboardingUiState,
) {
    if (!state.isReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(),
        ) {
            CircularProgressIndicator()
        }
        return
    }
    when (state.step) {
        OnboardingStep.WELCOME -> ColdStartScreen(
            data = ColdStartDisplayData(illustration = MorningFlowIllustrations.ColdStart),
            onBegin = viewModel::begin,
        )

        OnboardingStep.WAKE_UP_SETUP -> OnboardingFormScreen(
            data = OnboardingFormDisplayData(
                earliestWakeUp = state.earliestWakeUp,
                latestWakeUp = state.latestWakeUp,
                readyBy = state.readyBy,
                activeDays = state.activeDays,
                errors = state.errors,
            ),
            visualState = if (state.errors.isEmpty()) {
                OnboardingFormVisualState.Normal
            } else {
                OnboardingFormVisualState.Error
            },
            onNext = viewModel::next,
            onTimeSelected = { field, value ->
                when (field) {
                    com.morningflow.app.feature.onboarding.OnboardingField.EARLIEST_WAKE_UP -> viewModel.setEarliestWakeUp(value)
                    com.morningflow.app.feature.onboarding.OnboardingField.LATEST_WAKE_UP -> viewModel.setLatestWakeUp(value)
                    com.morningflow.app.feature.onboarding.OnboardingField.READY_BY -> viewModel.setReadyBy(value)
                    com.morningflow.app.feature.onboarding.OnboardingField.ACTIVE_DAYS -> Unit
                }
            },
            onDayToggle = viewModel::toggleActiveDay,
        )

        OnboardingStep.PERMISSIONS -> OnboardingPermissionRoute(
            viewModel = viewModel,
            onNext = viewModel::completePermissions,
        )

        OnboardingStep.COMPLETE -> Unit
    }
}

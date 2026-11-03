package com.morningflow.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.morningflow.app.core.data.AppContainer
import com.morningflow.app.feature.history.HistoryViewModel
import com.morningflow.app.feature.home.HomeViewModel
import com.morningflow.app.feature.onboarding.OnboardingViewModel
import com.morningflow.app.feature.settings.SettingsViewModel
import com.morningflow.app.feature.templates.TemplatesViewModel

/** Creates feature ViewModels from the application's existing dependency container. */
class AppFeatureViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(OnboardingViewModel::class.java) -> OnboardingViewModel(
            settingsRepository = container.settingsRepository,
            templateRepository = container.templateRepository,
        )

        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
            templateRepository = container.templateRepository,
            settingsRepository = container.settingsRepository,
            weatherLocationState = container.weatherLocationCoordinator.state,
            calendarRepository = container.calendarRepository,
            refreshWeatherLocation = { container.weatherLocationCoordinator.refresh() },
        )

        modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(
            records = container.sessionRecordRepository.records,
        )

        modelClass.isAssignableFrom(TemplatesViewModel::class.java) -> TemplatesViewModel(
            templateRepository = container.templateRepository,
            settingsRepository = container.settingsRepository,
        )

        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
            settingsRepository = container.settingsRepository,
            wakeupScheduleStatus = container.wakeupScheduler.status,
        )

        else -> throw IllegalArgumentException("Unsupported feature ViewModel: ${modelClass.name}")
    } as T
}

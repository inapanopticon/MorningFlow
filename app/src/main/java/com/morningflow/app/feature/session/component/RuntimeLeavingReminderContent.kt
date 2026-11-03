package com.morningflow.app.feature.session.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.morningflow.app.app.MorningFlowApplication
import com.morningflow.app.core.domain.runtime.LeavingContent
import com.morningflow.app.core.domain.runtime.SessionWeatherLocationLoader
import com.morningflow.app.core.domain.runtime.LeavingReminderLoader
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.feature.session.LeavingReminderPresenter
import com.morningflow.app.feature.session.MorningSessionDisplayData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/** Stage-scoped loading: leaving the composition cancels queries and discards stale results. */
@Composable
internal fun RuntimeLeavingReminderContent(data: MorningSessionDisplayData, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        LeavingReminderContent(data, modifier)
        return
    }
    val application = LocalContext.current.applicationContext as MorningFlowApplication
    val container = application.appContainer
    val lifecycleOwner = LocalLifecycleOwner.current
    var content by remember(data.stepText) { mutableStateOf(LeavingReminderPresenter.loading()) }
    LaunchedEffect(container, data.stepText, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            content = LeavingReminderPresenter.loading()
            try {
                val ready = withTimeoutOrNull(5_000) { container.settingsRepository.awaitReady(); true } == true
                if (!ready) {
                    content = LeavingReminderPresenter.map(unavailableContent(), AppSettings())
                    return@repeatOnLifecycle
                }
                val settings = container.settingsRepository.settings.value
                val loader = LeavingReminderLoader(container.weatherRepository, container.calendarRepository,
                    location = {
                        when (val result = container.locationRepository.refreshCurrent()) {
                            is DataResult.Success -> DataResult.Success(result.value.displayLabel)
                            is DataResult.Unavailable -> result
                        }
                    }, locationWeather = SessionWeatherLocationLoader(
                        container.locationRepository, container.weatherLocationRepository
                    )::load)
                supervisorScope {
                    val calendarTask = async {
                        try {
                            loader.loadCalendar()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            DataResult.Unavailable("QUERY_FAILED")
                        }
                    }
                    val locationWeatherTask = async {
                        try {
                            loader.loadLocationWeather()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            DataResult.Unavailable("QUERY_FAILED") to
                                DataResult.Unavailable("QUERY_FAILED")
                        }
                    }
                    launch {
                        val mapped = LeavingReminderPresenter.map(
                            LeavingContent(
                                DataResult.Unavailable("LOADING"),
                                DataResult.Unavailable("LOADING"),
                                calendarTask.await(),
                                settings.thingsToBring.toList(),
                            ), settings,
                        )
                        content = content.copy(
                            scheduleItems = mapped.scheduleItems,
                            thingsToBring = mapped.thingsToBring,
                        )
                    }
                    launch {
                        val pair = locationWeatherTask.await()
                        val mapped = LeavingReminderPresenter.map(
                            LeavingContent(
                                pair.first,
                                pair.second,
                                DataResult.Success(emptyList()),
                                settings.thingsToBring.toList(),
                            ), settings,
                        )
                        content = content.copy(
                            locationWeather = mapped.locationWeather,
                            reminders = mapped.reminders,
                            thingsToBring = mapped.thingsToBring,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                content = LeavingReminderPresenter.map(unavailableContent(), AppSettings())
            }
        }
    }
    LeavingReminderContent(data.copy(leavingReminder = content), modifier)
}

private fun unavailableContent() = LeavingContent(
    DataResult.Unavailable("QUERY_FAILED"), DataResult.Unavailable("QUERY_FAILED"),
    DataResult.Unavailable("QUERY_FAILED"), emptyList(),
)

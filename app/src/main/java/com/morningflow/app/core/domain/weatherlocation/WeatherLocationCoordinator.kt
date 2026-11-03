package com.morningflow.app.core.domain.weatherlocation

import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The single domain entry point for ViewModels and application lifecycle integration.
 * It owns published state and scheduling, while [WeatherLocationRefreshUseCase] owns
 * one location-then-weather refresh.
 */
class WeatherLocationCoordinator(
    private val refreshUseCase: WeatherLocationRefreshUseCase,
    private val clock: Clock = Clock.systemUTC(),
    refreshIntervalMinutes: Long = WeatherRefreshPolicy.DEFAULT_INTERVAL_MINUTES,
) {
    private val refreshMutex = Mutex()
    private val wakeSignal = Channel<Unit>(capacity = Channel.CONFLATED)
    private val _state = MutableStateFlow<WeatherLocationUiState>(WeatherLocationUiState.Idle)

    private var refreshPolicy = WeatherRefreshPolicy(refreshIntervalMinutes)
    private var nextAutoRefreshAtEpochMs: Long? = null
    private var autoRefreshJob: Job? = null

    val state: StateFlow<WeatherLocationUiState> = _state.asStateFlow()

    val refreshIntervalMinutes: Long
        get() = refreshPolicy.intervalMinutes

    /** Runs the refresh pipeline and publishes the resulting state. */
    suspend fun refresh(): WeatherLocationUiState {
        val result = refreshMutex.withLock {
            _state.value = WeatherLocationUiState.Loading
            val nextState = try {
                refreshUseCase.refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                WeatherLocationUiState.LocationUnavailable(LocationUnavailableReason.UNKNOWN)
            }
            _state.value = nextState
            nextState
        }
        scheduleNextRefresh()
        wakeSignal.trySend(Unit)
        return result
    }

    /** Updates the validated interval and wakes an active scheduler. */
    fun setRefreshIntervalMinutes(intervalMinutes: Long) {
        refreshPolicy = WeatherRefreshPolicy(intervalMinutes)
        scheduleNextRefresh()
        wakeSignal.trySend(Unit)
    }

    /** Starts automatic refresh while the supplied application/lifecycle scope is active. */
    fun startAutoRefresh(scope: CoroutineScope): Job {
        autoRefreshJob?.cancel()
        drainWakeSignal()
        val job = scope.launch {
            refreshIfStaleOrUnavailable()
            while (true) {
                val deadline = nextAutoRefreshAtEpochMs ?: clock.millis()
                val waitMillis = (deadline - clock.millis()).coerceAtLeast(0L)
                val woken = withTimeoutOrNull(waitMillis) { wakeSignal.receive() }
                if (woken == null) refresh()
            }
        }
        autoRefreshJob = job
        return job
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        drainWakeSignal()
    }

    private suspend fun refreshIfStaleOrUnavailable() {
        when (val currentState = state.value) {
            is WeatherLocationUiState.Ready -> {
                if (refreshPolicy.isStale(currentState.weather.fetchedAtEpochMs, clock.millis())) {
                    refresh()
                } else {
                    scheduleNextRefresh(currentState.weather.fetchedAtEpochMs)
                }
            }

            else -> refresh()
        }
    }

    private fun scheduleNextRefresh(fetchedAtEpochMs: Long? = null) {
        val baseTime = fetchedAtEpochMs ?: clock.millis()
        nextAutoRefreshAtEpochMs = baseTime + refreshPolicy.intervalMillis
    }

    private fun drainWakeSignal() {
        while (wakeSignal.tryReceive().isSuccess) {
            // Remove signals from a previous automatic refresh job.
        }
    }
}

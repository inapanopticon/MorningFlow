package com.morningflow.app.core.data

import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.RoutineTemplate
import com.morningflow.app.core.model.SensorSnapshot
import com.morningflow.app.core.model.SessionRecord
import com.morningflow.app.core.model.WeatherInfo
import com.morningflow.app.core.model.calendar.CalendarEvent
import kotlinx.coroutines.flow.StateFlow

interface TemplateRepository {
    val templates: StateFlow<List<RoutineTemplate>>

    /**
     * Suspends until [templates] holds the stored templates.
     *
     * A backing store that has to be read before it can answer starts [templates] at an
     * empty list, which is indistinguishable from a genuinely empty one. Nothing can act
     * on that: Recommended Morning always exists, so an empty list only ever means "not
     * read yet". Call this before reading [templates] from a screen that acts on what it
     * finds - onboarding writing the user's wake-up setup into Recommended Morning, for
     * instance - rather than reading `.value` straight after construction.
     *
     * Returns immediately once the first read has landed, so it is cheap to call again.
     */
    suspend fun awaitReady()

    suspend fun save(template: RoutineTemplate)

    suspend fun delete(templateId: String)

    suspend fun setDefault(templateId: String)
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    /**
     * Suspends until [settings] holds what was stored.
     *
     * A backing store that has to be read before it can answer starts [settings] at the
     * built-in defaults, which look exactly like settings the user genuinely chose. A
     * screen that shows a stored value, or code that decides something from one, has to
     * wait rather than read `.value` straight after construction - otherwise it shows
     * 07:00 to someone who set 06:15, and saving from that screen writes the default
     * back over their setting.
     *
     * Returns immediately once the first read has landed, so it is cheap to call again.
     */
    suspend fun awaitReady()

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface SessionRecordRepository {
    val records: StateFlow<List<SessionRecord>>

    suspend fun save(record: SessionRecord)
}

/**
 * Supplies the leaving reminder's weather line. Owned by the weather feature; the
 * session only depends on this interface so either implementation can back it.
 */
interface WeatherRepository {
    suspend fun getTodayWeather(): DataResult<WeatherInfo>
}

/** Supplies the leaving reminder's schedule line. */
interface CalendarRepository {
    suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>>
}

/**
 * Movement and ambient-light readings for the physical wake-up stage, plus the
 * wake-up detections that drive the morning prompt. Owned by the sensor feature; the
 * session reads the snapshot and never talks to hardware itself.
 */
interface SensorRepository {
    val snapshot: StateFlow<SensorSnapshot>

    fun startDemoSignals()

    fun pause()

    fun stop()

    fun reset()
}

/**
 * Arms the alarm that watches for a wake-up.
 *
 * Detection is not day-scoped: it runs every day between the schedule's earliest and
 * latest wake-up, independent of which days any template applies to.
 */
enum class WakeupScheduleFailureReason {
    ZERO_LENGTH_WINDOW,
    END_BEFORE_START,
    EXACT_ACCESS_REQUIRED,
    PLATFORM_REJECTED,
}

sealed interface WakeupScheduleStatus {
    data object NotScheduled : WakeupScheduleStatus

    data object Scheduled : WakeupScheduleStatus

    data class Failed(
        val reason: WakeupScheduleFailureReason,
    ) : WakeupScheduleStatus
}

interface WakeupScheduler {
    val status: StateFlow<WakeupScheduleStatus>

    suspend fun schedule(settings: AppSettings): WakeupScheduleStatus

    suspend fun cancel(): WakeupScheduleStatus
}

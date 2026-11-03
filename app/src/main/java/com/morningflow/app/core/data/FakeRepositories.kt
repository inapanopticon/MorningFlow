package com.morningflow.app.core.data

import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.Behaviour
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.RECOMMENDED_TEMPLATE_ID
import com.morningflow.app.core.model.RoutineModule
import com.morningflow.app.core.model.RoutineTemplate
import com.morningflow.app.core.model.SensorSnapshot
import com.morningflow.app.core.model.SessionRecord
import com.morningflow.app.core.model.GlobalMorningSchedule
import com.morningflow.app.core.model.WeatherInfo
import com.morningflow.app.core.model.calendar.CalendarEvent
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The three required steps a template may reorder but never drop. */
private val LOCKED_BEHAVIOURS = setOf(
    BehaviourKey.LEAVE_BED,
    BehaviourKey.CHANGE_INTO_HOME_CLOTHES,
    BehaviourKey.LIGHT_REMINDER,
)

internal fun moduleOf(
    type: ModuleType,
    enabled: Boolean = true,
    squatTarget: Int = 0,
    stepTarget: Int = 0,
    modeOverrides: Map<BehaviourKey, BehaviourMode> = emptyMap(),
) = RoutineModule(
    type = type,
    enabled = enabled,
    squatTarget = if (enabled) squatTarget else 0,
    stepTarget = if (enabled) stepTarget else 0,
    behaviours = BehaviourKey.forModule(type).map { key ->
        val mode = if (enabled) {
            modeOverrides[key] ?: Behaviour.defaultModeFor(key)
        } else {
            BehaviourMode.OFF
        }
        Behaviour(key = key, mode = mode, locked = key in LOCKED_BEHAVIOURS)
    },
)

private val WEEKDAYS = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

internal fun standardModules(
    physicalSquatTarget: Int = 10,
    physicalStepTarget: Int = 50,
    modeOverrides: Map<BehaviourKey, BehaviourMode> = emptyMap(),
    disabledModules: Set<ModuleType> = emptySet(),
) = ModuleType.entries.map { type ->
    moduleOf(
        type = type,
        enabled = type !in disabledModules,
        squatTarget = if (type == ModuleType.PHYSICAL_WAKE_UP) physicalSquatTarget else 0,
        stepTarget = if (type == ModuleType.PHYSICAL_WAKE_UP) physicalStepTarget else 0,
        modeOverrides = modeOverrides,
    )
}

/** The stable baseline Template every install starts from. */
fun recommendedTemplate(
    id: String = RECOMMENDED_TEMPLATE_ID,
    name: String = "Recommended Morning",
) = RoutineTemplate(
    id = id,
    name = name,
    applicableDays = WEEKDAYS,
    applicableStart = LocalTime.of(6, 0),
    priority = 5,
    modules = standardModules(),
    isDefault = true,
)

/**
 * The starter templates every fresh install ships with: Recommended Morning alone. Users
 * build everything else themselves. Shared by [FakeTemplateRepository] and
 * [RoomTemplateRepository] so an empty real database seeds itself with the exact same
 * content as the in-memory build.
 */
fun defaultTemplateSeed(): List<RoutineTemplate> {
    return listOf(recommendedTemplate())
}

class FakeTemplateRepository(
    initialTemplates: List<RoutineTemplate> = defaultTemplateSeed(),
) : TemplateRepository {
    private val _templates = MutableStateFlow(initialTemplates)
    override val templates: StateFlow<List<RoutineTemplate>> = _templates.asStateFlow()

    /** Seeded in the constructor, so there is never anything to wait for. */
    override suspend fun awaitReady() = Unit

    override suspend fun save(template: RoutineTemplate) {
        _templates.value = _templates.value
            .filterNot { it.id == template.id }
            .plus(template)
    }

    /**
     * Refuses to delete Recommended Morning, and hands the default flag on when the
     * deleted template was holding it.
     */
    override suspend fun delete(templateId: String) {
        val deleted = _templates.value.firstOrNull { it.id == templateId } ?: return
        if (!deleted.isDeletable) return
        val remaining = _templates.value.filterNot { it.id == templateId }
        _templates.value = if (!deleted.isDefault || remaining.isEmpty()) {
            remaining
        } else {
            val heir = remaining.defaultHeirId()
            remaining.map { it.copy(isDefault = it.id == heir) }
        }
    }

    override suspend fun setDefault(templateId: String) {
        if (_templates.value.none { it.id == templateId }) return
        _templates.value = _templates.value.map {
            it.copy(isDefault = it.id == templateId)
        }
    }
}

/**
 * Who inherits the default flag: Recommended Morning when it is still there, otherwise
 * the highest-priority template left. [RoomTemplateRepository] resolves this the same
 * way in SQL - see [com.morningflow.app.core.database.TemplateDao.deleteTemplateKeepingDefault].
 */
private fun List<RoutineTemplate>.defaultHeirId(): String? =
    firstOrNull { it.id == RECOMMENDED_TEMPLATE_ID }?.id ?: maxByOrNull { it.priority }?.id

class FakeSettingsRepository : SettingsRepository {
    /** Held in memory from construction, so there is never anything to wait for. */
    override suspend fun awaitReady() = Unit

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
    }
}

class FakeSessionRecordRepository : SessionRecordRepository {
    private val _records = MutableStateFlow<List<SessionRecord>>(emptyList())
    override val records: StateFlow<List<SessionRecord>> = _records.asStateFlow()

    override suspend fun save(record: SessionRecord) {
        if (_records.value.any { it.id == record.id }) return
        _records.value = listOf(record) + _records.value
    }
}

class FakeWeatherRepository : WeatherRepository {
    override suspend fun getTodayWeather(): DataResult<WeatherInfo> = DataResult.Success(
        WeatherInfo(
            city = "Melbourne",
            condition = "Light rain",
            temperatureCelsius = 8,
            rainProbability = 80,
            uvIndex = 7,
        ),
    )
}

class FakeCalendarRepository(
    private val result: DataResult<List<CalendarEvent>> = DataResult.Success(
        listOf(
            CalendarEvent(
                title = "Project meeting",
                startsAt = Instant.parse("2026-09-29T09:00:00Z"),
                endsAt = Instant.parse("2026-09-29T10:00:00Z"),
                isAllDay = false,
            ),
            CalendarEvent(
                title = "Team check-in",
                startsAt = Instant.parse("2026-09-29T14:30:00Z"),
                endsAt = Instant.parse("2026-09-29T15:00:00Z"),
                isAllDay = false,
            ),
        ),
    ),
) : CalendarRepository {
    override suspend fun getRemainingTodayEvents(): DataResult<List<CalendarEvent>> = result
}

class FakeWakeupScheduler : WakeupScheduler {
    private val _status = MutableStateFlow<WakeupScheduleStatus>(WakeupScheduleStatus.NotScheduled)
    override val status: StateFlow<WakeupScheduleStatus> = _status.asStateFlow()

    /** The schedule the last [schedule] call armed, so callers can see what was armed. */
    private val _scheduledFor = MutableStateFlow<GlobalMorningSchedule?>(null)
    val scheduledFor: StateFlow<GlobalMorningSchedule?> = _scheduledFor.asStateFlow()

    /** Detection runs daily, so this is one arming rather than one per set of days. */
    override suspend fun schedule(settings: AppSettings): WakeupScheduleStatus {
        val schedule = settings.schedule
        val failure = when {
            schedule.earliestWakeUp == schedule.latestWakeUp ->
                WakeupScheduleFailureReason.ZERO_LENGTH_WINDOW
            schedule.latestWakeUp.isBefore(schedule.earliestWakeUp) ->
                WakeupScheduleFailureReason.END_BEFORE_START
            else -> null
        }
        _scheduledFor.value = schedule.takeIf { failure == null }
        _status.value = failure?.let(WakeupScheduleStatus::Failed)
            ?: WakeupScheduleStatus.Scheduled
        return _status.value
    }

    override suspend fun cancel(): WakeupScheduleStatus {
        _scheduledFor.value = null
        _status.value = WakeupScheduleStatus.NotScheduled
        return _status.value
    }
}

class FakeSensorRepository(
    clock: Clock = Clock.systemUTC(),
    wakeUpRepeatInterval: Duration = WakeUpDetectionGate.DEFAULT_MINIMUM_REPEAT_INTERVAL,
) : SensorRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _snapshot = MutableStateFlow(SensorSnapshot())
    override val snapshot: StateFlow<SensorSnapshot> = _snapshot.asStateFlow()
    private var signalJob: Job? = null
    private val wakeUpGate = WakeUpDetectionGate(wakeUpRepeatInterval, clock)

    override fun startDemoSignals() {
        if (signalJob?.isActive == true) return
        signalJob = scope.launch {
            var tick = 0
            while (true) {
                delay(1_000)
                tick += 1
                _snapshot.value = _snapshot.value.copy(
                    squatCount = (_snapshot.value.squatCount + 1).coerceAtMost(DEMO_SQUAT_CAP),
                    stepCount = (_snapshot.value.stepCount + 3).coerceAtMost(DEMO_STEP_CAP),
                    brightnessChanged = tick >= 4,
                )
                if (tick >= 2) reportWakeUp()
            }
        }
    }

    /**
     * Publishes a wake-up detection, stamped with the time the gate accepts.
     *
     * Signals arriving too soon after the last accepted one leave the snapshot alone, so
     * the demo build behaves like the real algorithm rather than restamping every tick.
     */
    fun reportWakeUp() {
        val detectedAt = wakeUpGate.offer() ?: return
        _snapshot.value = _snapshot.value.copy(
            wakeUpDetected = true,
            wakeUpDetectedAt = detectedAt,
        )
    }

    override fun pause() {
        signalJob?.cancel()
        signalJob = null
    }

    override fun stop() {
        pause()
    }

    override fun reset() {
        pause()
        wakeUpGate.reset()
        _snapshot.value = SensorSnapshot()
    }

    private companion object {
        const val DEMO_SQUAT_CAP = 10
        const val DEMO_STEP_CAP = 40
    }
}

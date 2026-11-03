package com.morningflow.app.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import kotlin.math.roundToInt

/** The eight stages of a morning routine, in the order they run. */
enum class ModuleType(val displayName: String) {
    LEAVE_BED("Leave Bed"),
    PHYSICAL_WAKE_UP("Physical Wake-up"),
    MORNING_RESET("Morning Reset"),
    MEAL_PREPARATION("Meal Preparation"),
    WASH_AND_GROOM("Wash & Groom"),
    BREAKFAST("Breakfast"),
    GET_READY("Get Ready"),
    LEAVING_REMINDER("Leaving Reminder"),
}

/**
 * A single guided step. The key identifies which narration script the step plays and
 * is stable across edits, so a renamed or reordered step keeps its audio.
 *
 * Timed steps come in three lengths; [mediumSeconds] is the default and matches the
 * original 30-minute routine. Sensor steps (squats, walking) run until their target, so
 * their lengths are 0 and their time limit comes from [Behaviour.plannedSeconds].
 */
enum class BehaviourKey(
    val module: ModuleType,
    val label: String,
    val shortSeconds: Int,
    val mediumSeconds: Int = shortSeconds,
    val longSeconds: Int = mediumSeconds,
) {
    LEAVE_BED(ModuleType.LEAVE_BED, "Leave Bed", 30, 40, 60),
    CHANGE_INTO_HOME_CLOTHES(ModuleType.LEAVE_BED, "Change into Comfy Clothes", 20, 30, 60),
    LIGHT_REMINDER(ModuleType.LEAVE_BED, "Light Reminder", 15, 20, 30),

    STRETCHING(ModuleType.PHYSICAL_WAKE_UP, "Stretching", 60, 105, 150),
    SQUATS(ModuleType.PHYSICAL_WAKE_UP, "Squats", 0),
    WALKING(ModuleType.PHYSICAL_WAKE_UP, "Walking", 0),

    TURN_ON_LIGHT(ModuleType.MORNING_RESET, "Turn on Light", 15, 20, 30),
    OPEN_CURTAINS(ModuleType.MORNING_RESET, "Open Curtains", 40, 60, 90),
    MAKE_BED(ModuleType.MORNING_RESET, "Make Bed", 60, 90, 120),
    TIDY_ROOM(ModuleType.MORNING_RESET, "Tidy Room", 60, 100, 150),

    MEAL_PREPARATION(ModuleType.MEAL_PREPARATION, "Meal Preparation", 150, 210, 300),

    BRUSH_TEETH(ModuleType.WASH_AND_GROOM, "Brush Teeth", 120, 180, 240),
    WASH_FACE(ModuleType.WASH_AND_GROOM, "Wash Face", 60, 105, 150),
    SKINCARE(ModuleType.WASH_AND_GROOM, "Skincare", 45, 75, 120),

    BREAKFAST(ModuleType.BREAKFAST, "Breakfast", 150, 240, 360),

    CHANGE_CLOTHES(ModuleType.GET_READY, "Change Clothes", 60, 90, 150),
    MAKEUP(ModuleType.GET_READY, "Makeup", 60, 120, 240),
    OTHER_PERSONAL_PREPARATION(ModuleType.GET_READY, "Other Personal Preparation", 30, 60, 120),

    LEAVING_REMINDER(ModuleType.LEAVING_REMINDER, "Leaving Reminder", 120),

    ;

    companion object {
        fun forModule(module: ModuleType): List<BehaviourKey> = entries.filter { it.module == module }
    }

    val isSensor: Boolean
        get() = this == SQUATS || this == WALKING
}

enum class BehaviourMode { SHORT, MEDIUM, LONG, OFF, ON }

/**
 * A behaviour as it sits inside a template.
 *
 * The narration and its duration come from [key], so only what a user can change is
 * stored: whether the step runs, and where it sits in the stage. [locked] steps may
 * still be skipped while a session runs; they just cannot be removed from a template.
 */
data class Behaviour(
    val id: String = UUID.randomUUID().toString(),
    val key: BehaviourKey,
    val mode: BehaviourMode = defaultModeFor(key),
    val locked: Boolean = false,
) {
    init {
        require(mode in allowedModes) { "$mode is not valid for ${key.name}" }
    }

    val enabled: Boolean get() = mode != BehaviourMode.OFF
    val label: String get() = key.label

    fun plannedSeconds(squatTarget: Int = 0, stepTarget: Int = 0): Int = when (mode) {
        BehaviourMode.OFF -> 0
        BehaviourMode.SHORT -> key.shortSeconds
        BehaviourMode.MEDIUM -> key.mediumSeconds
        BehaviourMode.LONG -> key.longSeconds
        BehaviourMode.ON -> when (key) {
            BehaviourKey.SQUATS -> 33 + squatTarget * 3
            BehaviourKey.WALKING -> (31 + stepTarget * 0.6).roundToInt()
            else -> 0
        }
    }

    companion object {
        fun defaultModeFor(key: BehaviourKey): BehaviourMode =
            if (key.isSensor) BehaviourMode.ON else BehaviourMode.MEDIUM
    }

    private val allowedModes: Set<BehaviourMode>
        get() = when {
            key.isSensor -> setOf(BehaviourMode.ON, BehaviourMode.OFF)
            // Getting up and changing are what start the routine, so they cannot be switched off.
            key == BehaviourKey.LEAVE_BED || key == BehaviourKey.CHANGE_INTO_HOME_CLOTHES ->
                setOf(BehaviourMode.SHORT, BehaviourMode.MEDIUM, BehaviourMode.LONG)
            else -> setOf(BehaviourMode.SHORT, BehaviourMode.MEDIUM, BehaviourMode.LONG, BehaviourMode.OFF)
        }
}

data class RoutineModule(
    val id: String = UUID.randomUUID().toString(),
    val type: ModuleType,
    val enabled: Boolean = true,
    val behaviours: List<Behaviour> = emptyList(),
    val squatTarget: Int = 0,
    val stepTarget: Int = 0,
) {
    val plannedSeconds: Int
        get() = if (!enabled) 0 else behaviours.sumOf {
            it.plannedSeconds(squatTarget = squatTarget, stepTarget = stepTarget)
        }
}

/**
 * Recommended Morning's fixed id.
 *
 * Onboarding writes the user's wake-up setup into this template, and it is the template
 * the default flag falls back to, so it always exists - see [RoutineTemplate.isDeletable].
 */
const val RECOMMENDED_TEMPLATE_ID = "recommended-30"

data class RoutineTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val applicableDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val applicableStart: LocalTime = LocalTime.of(5, 0),
    val applicableEnd: LocalTime = LocalTime.of(11, 0),
    val priority: Int = 1,
    val modules: List<RoutineModule>,
    val isDefault: Boolean = false,
) {
    val plannedSeconds: Int get() = modules.filter { it.enabled }.sumOf { it.plannedSeconds }

    /**
     * Recommended Morning is the one template that always exists, so it cannot be
     * deleted - it is editable instead, and re-seeding it would throw away the user's
     * own applicable days and behaviour choices. A screen offering Delete should hide
     * that action when this is false; the repositories refuse it either way.
     */
    val isDeletable: Boolean get() = id != RECOMMENDED_TEMPLATE_ID
}

/**
 * Turning voice guidance off silences it without stopping the morning: the countdown
 * runs on its own, so stages still begin and end on time with nothing being spoken.
 */
data class AudioSettings(
    val backgroundMusicEnabled: Boolean = true,
    val backgroundMusicVolume: Float = DEFAULT_VOLUME,
    val voiceGuidanceEnabled: Boolean = true,
    val voiceGuidanceVolume: Float = DEFAULT_VOLUME,
    val audioCuesEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_VOLUME = 0.3f
    }
}

/**
 * Which context-aware reminders the user wants, and how insistent each one should be.
 *
 * The umbrella and jacket reminders hold the cutoff itself rather than a sensitivity
 * name, so what the user picked in settings is the number the weather is compared
 * against - see [com.morningflow.app.core.domain.LeavingReminderNarration]. Null is Off.
 * Whether a reminder actually fires is still the day's weather's call, not this.
 */
data class ReminderPreferences(
    /** Remind at or above this chance of rain, as a percentage. Null is Off. */
    val umbrellaRainPercent: Int? = DEFAULT_UMBRELLA_RAIN_PERCENT,
    /** Remind at or below this temperature, in Celsius. Null is Off. */
    val jacketTemperatureCelsius: Int? = DEFAULT_JACKET_TEMPERATURE_CELSIUS,
    /** Off by default: most mornings do not need it, unlike rain and cold. */
    val sunscreen: Boolean = false,
) {
    companion object {
        const val DEFAULT_UMBRELLA_RAIN_PERCENT = 40
        const val DEFAULT_JACKET_TEMPERATURE_CELSIUS = 8

        /**
         * The cutoffs settings offers, alongside Off. Persistence rejects anything else,
         * so a stored value can always be shown as the selected option.
         */
        val UMBRELLA_RAIN_PERCENT_OPTIONS = listOf(20, 40, 60, 80)
        val JACKET_TEMPERATURE_OPTIONS = listOf(5, 8, 10, 15)
    }
}

/**
 * The packing list a fresh install starts with.
 *
 * These are plain strings rather than string resources because the list is data the user
 * edits - items can be renamed, removed or added - and the leaving reminder reads them
 * straight back out to speak them.
 */
val DEFAULT_THINGS_TO_BRING: List<String> =
    listOf("Keys", "Phone", "Wallet")

/** How far the user got through onboarding. The app resumes at the stored step. */
enum class OnboardingStep { WELCOME, WAKE_UP_SETUP, PERMISSIONS, COMPLETE }

/**
 * The three long-lived times MorningFlow plans a morning around.
 *
 * Deliberately not day-scoped. Wake-up detection runs every day between
 * [earliestWakeUp] and [latestWakeUp], independent of any template's applicable days -
 * the days chosen during onboarding seed [RoutineTemplate.applicableDays] on
 * Recommended Morning and are not kept here.
 */
data class GlobalMorningSchedule(
    val earliestWakeUp: LocalTime = LocalTime.of(7, 0),
    val latestWakeUp: LocalTime = LocalTime.of(7, 30),
    val readyBy: LocalTime = LocalTime.of(9, 0),
) {
    val isValid: Boolean get() = earliestWakeUp.isBefore(latestWakeUp)

    fun covers(time: LocalTime): Boolean =
        !time.isBefore(earliestWakeUp) && time.isBefore(latestWakeUp)
}

/** Light or dark look; [SYSTEM] follows the phone. */
enum class Appearance { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboardingStep: OnboardingStep = OnboardingStep.WELCOME,
    val schedule: GlobalMorningSchedule = GlobalMorningSchedule(),
    val audio: AudioSettings = AudioSettings(),
    val reminders: ReminderPreferences = ReminderPreferences(),
    val thingsToBring: List<String> = DEFAULT_THINGS_TO_BRING,
    val appearance: Appearance = Appearance.SYSTEM,
) {
    /**
     * Derived rather than stored, so a finished onboarding cannot disagree with the step
     * the user is resumed at.
     */
    val onboardingComplete: Boolean get() = onboardingStep == OnboardingStep.COMPLETE

    /** Detection is not day-scoped, so only the time of day decides this. */
    fun isInsideWakeWindow(time: LocalTime): Boolean = schedule.covers(time)
}

data class WeatherInfo(
    val city: String,
    val condition: String,
    val temperatureCelsius: Int,
    val rainProbability: Int,
    val uvIndex: Int,
)

data class SensorSnapshot(
    val squatCount: Int = 0,
    val stepCount: Int = 0,
    val brightnessChanged: Boolean = false,
    val wakeUpDetected: Boolean = false,
    /**
     * When the most recent wake-up detection happened, or null before the first one.
     *
     * [wakeUpDetected] alone cannot express "detected again": flipping it from true to
     * true leaves the snapshot structurally equal, and a `StateFlow` drops equal values,
     * so a repeat reminder would never reach its observers. Each accepted detection
     * stamps a fresh instant here, which makes the snapshot a new value.
     *
     * What counts as a separate detection - rather than the same one still ongoing - is
     * the producer's call; see
     * [com.morningflow.app.core.data.WakeUpDetectionGate].
     */
    val wakeUpDetectedAt: Instant? = null,
)

enum class CompletionKind { VERIFIED, TIMED, GUIDANCE, SKIPPED }

data class ModuleResult(
    val moduleType: ModuleType,
    val completionKind: CompletionKind,
)

enum class SessionStatus { NOT_STARTED, RUNNING, PAUSED, COMPLETED, EXITED }

data class SessionUiState(
    val status: SessionStatus = SessionStatus.NOT_STARTED,
    val templateName: String = "",
    val modules: List<RoutineModule> = emptyList(),
    val currentModuleIndex: Int = 0,
    val currentBehaviourIndex: Int = 0,
    val remainingSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val results: List<ModuleResult> = emptyList(),
    val sensor: SensorSnapshot = SensorSnapshot(),
) {
    val currentModule: RoutineModule? get() = modules.getOrNull(currentModuleIndex)

    val currentBehaviour: Behaviour?
        get() = currentModule?.behaviours?.filter { it.enabled }?.getOrNull(currentBehaviourIndex)

    val progress: Float
        get() = if (modules.isEmpty()) 0f else currentModuleIndex.toFloat() / modules.size.toFloat()
}

data class SessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val templateId: String = "",
    val templateName: String,
    val startedAt: Instant = Instant.now(),
    val endedAt: Instant = Instant.now(),
    val actualDurationSeconds: Int,
    val status: SessionStatus = SessionStatus.COMPLETED,
    val results: List<ModuleResult>,
)

sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>
    data class Unavailable(val reason: String) : DataResult<Nothing>
}

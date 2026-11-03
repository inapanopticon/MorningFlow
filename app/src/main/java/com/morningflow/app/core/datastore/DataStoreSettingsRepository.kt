package com.morningflow.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.morningflow.app.core.data.SettingsRepository
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.Appearance
import com.morningflow.app.core.model.AudioSettings
import com.morningflow.app.core.model.GlobalMorningSchedule
import com.morningflow.app.core.model.OnboardingStep
import com.morningflow.app.core.model.ReminderPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalTime

private const val LIST_DELIMITER = ""

/** Record and field separators of the retired [Keys.LEGACY_WAKE_WINDOWS] value. */
private const val LEGACY_WINDOW_DELIMITER = ""
private const val LEGACY_WINDOW_FIELD_DELIMITER = ""

private object Keys {
    val ONBOARDING_STEP = stringPreferencesKey("onboarding_step")
    val EARLIEST_WAKE_UP = stringPreferencesKey("earliest_wake_up")
    val LATEST_WAKE_UP = stringPreferencesKey("latest_wake_up")
    val READY_BY_TIME = stringPreferencesKey("ready_by_time")
    val BACKGROUND_MUSIC_ENABLED = booleanPreferencesKey("background_music_enabled")
    val BACKGROUND_MUSIC_VOLUME = floatPreferencesKey("background_music_volume")
    val VOICE_GUIDANCE_ENABLED = booleanPreferencesKey("voice_guidance_enabled")
    val VOICE_GUIDANCE_VOLUME = floatPreferencesKey("voice_guidance_volume")
    val AUDIO_CUES_ENABLED = booleanPreferencesKey("audio_cues_enabled")
    val REMINDER_UMBRELLA_RAIN_PERCENT = intPreferencesKey("reminder_umbrella_rain_percent")
    val REMINDER_JACKET_TEMPERATURE = intPreferencesKey("reminder_jacket_temperature_celsius")
    val REMINDER_SUNSCREEN = booleanPreferencesKey("reminder_sunscreen")
    val THINGS_TO_BRING = stringPreferencesKey("things_to_bring")
    val APPEARANCE = stringPreferencesKey("appearance")

    /**
     * Written by the build whose umbrella and jacket reminders were a plain on/off,
     * before the user could choose the cutoff. Read only: on migrates to the default
     * cutoff and off to no reminder at all; see [Preferences.readReminderThreshold].
     */
    val LEGACY_REMINDER_UMBRELLA = booleanPreferencesKey("reminder_umbrella")
    val LEGACY_REMINDER_JACKET = booleanPreferencesKey("reminder_jacket")

    /**
     * Written by the build that only knew whether onboarding had finished, before the
     * flow was split into resumable steps. Read only, to place an existing install at
     * [OnboardingStep.COMPLETE]; see [Preferences.readOnboardingStep].
     */
    val LEGACY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

    /**
     * Written by the build whose wake window carried its own set of days. Read only, for
     * the earliest and latest times of its first window; see
     * [Preferences.readLegacyWindowTime]. The stored days are dropped on purpose -
     * detection is no longer day-scoped, and the days picked during onboarding now live
     * on Recommended Morning's applicable days instead.
     */
    val LEGACY_WAKE_WINDOWS = stringPreferencesKey("wake_windows")
}

/**
 * Persists [AppSettings] (including [AudioSettings]) in a Preferences DataStore.
 *
 * Every field is read defensively: a missing or malformed value falls back to the
 * matching [AppSettings] default instead of failing the whole read, so one corrupted
 * entry cannot wipe out the rest of the user's settings. Retired keys are still read as
 * a last resort before that default, which is what carries an existing install across a
 * model change; nothing writes them again.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope
) : SettingsRepository {

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Completed by the first stored value, which is what [awaitReady] waits for. */
    private val firstLoad = CompletableDeferred<Unit>()

    init {
        // Collected by hand rather than with stateIn(Eagerly), because the signal has to
        // be raised *after* the value is published. An onEach ahead of stateIn runs
        // upstream of the collector that assigns it, so a waiter could wake to find the
        // defaults still in place - which is the very thing awaitReady exists to prevent.
        scope.launch {
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }
                .map { it.toAppSettings() }
                .collect {
                    _settings.value = it
                    firstLoad.complete(Unit)
                }
        }
    }

    override suspend fun awaitReady() {
        firstLoad.await()
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toAppSettings())
            prefs.writeAppSettings(updated)
        }
    }
}

private fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        onboardingStep = readOnboardingStep(defaults.onboardingStep),
        schedule = GlobalMorningSchedule(
            earliestWakeUp = readLocalTime(Keys.EARLIEST_WAKE_UP)
                ?: readLegacyWindowTime(LEGACY_EARLIEST_FIELD)
                ?: defaults.schedule.earliestWakeUp,
            latestWakeUp = readLocalTime(Keys.LATEST_WAKE_UP)
                ?: readLegacyWindowTime(LEGACY_LATEST_FIELD)
                ?: defaults.schedule.latestWakeUp,
            readyBy = readLocalTime(Keys.READY_BY_TIME) ?: defaults.schedule.readyBy,
        ),
        audio = AudioSettings(
            backgroundMusicEnabled = this[Keys.BACKGROUND_MUSIC_ENABLED]
                ?: defaults.audio.backgroundMusicEnabled,
            backgroundMusicVolume = this[Keys.BACKGROUND_MUSIC_VOLUME]
                ?: defaults.audio.backgroundMusicVolume,
            voiceGuidanceEnabled = this[Keys.VOICE_GUIDANCE_ENABLED]
                ?: defaults.audio.voiceGuidanceEnabled,
            voiceGuidanceVolume = this[Keys.VOICE_GUIDANCE_VOLUME]
                ?: defaults.audio.voiceGuidanceVolume,
            audioCuesEnabled = this[Keys.AUDIO_CUES_ENABLED] ?: defaults.audio.audioCuesEnabled
        ),
        reminders = ReminderPreferences(
            umbrellaRainPercent = readReminderThreshold(
                key = Keys.REMINDER_UMBRELLA_RAIN_PERCENT,
                legacyKey = Keys.LEGACY_REMINDER_UMBRELLA,
                options = ReminderPreferences.UMBRELLA_RAIN_PERCENT_OPTIONS,
                default = defaults.reminders.umbrellaRainPercent,
            ),
            jacketTemperatureCelsius = readReminderThreshold(
                key = Keys.REMINDER_JACKET_TEMPERATURE,
                legacyKey = Keys.LEGACY_REMINDER_JACKET,
                options = ReminderPreferences.JACKET_TEMPERATURE_OPTIONS,
                default = defaults.reminders.jacketTemperatureCelsius,
            ),
            sunscreen = this[Keys.REMINDER_SUNSCREEN] ?: defaults.reminders.sunscreen
        ),
        thingsToBring = readStringList(Keys.THINGS_TO_BRING, defaults.thingsToBring),
        appearance = this[Keys.APPEARANCE]
            ?.let { raw -> Appearance.entries.firstOrNull { it.name == raw } }
            ?: defaults.appearance,
    )
}

private fun MutablePreferences.writeAppSettings(settings: AppSettings) {
    this[Keys.ONBOARDING_STEP] = settings.onboardingStep.name
    this[Keys.EARLIEST_WAKE_UP] = settings.schedule.earliestWakeUp.toString()
    this[Keys.LATEST_WAKE_UP] = settings.schedule.latestWakeUp.toString()
    this[Keys.READY_BY_TIME] = settings.schedule.readyBy.toString()
    this[Keys.BACKGROUND_MUSIC_ENABLED] = settings.audio.backgroundMusicEnabled
    this[Keys.BACKGROUND_MUSIC_VOLUME] = settings.audio.backgroundMusicVolume
    this[Keys.VOICE_GUIDANCE_ENABLED] = settings.audio.voiceGuidanceEnabled
    this[Keys.VOICE_GUIDANCE_VOLUME] = settings.audio.voiceGuidanceVolume
    this[Keys.AUDIO_CUES_ENABLED] = settings.audio.audioCuesEnabled
    this[Keys.REMINDER_UMBRELLA_RAIN_PERCENT] =
        settings.reminders.umbrellaRainPercent ?: REMINDER_OFF
    this[Keys.REMINDER_JACKET_TEMPERATURE] =
        settings.reminders.jacketTemperatureCelsius ?: REMINDER_OFF
    this[Keys.REMINDER_SUNSCREEN] = settings.reminders.sunscreen
    this[Keys.THINGS_TO_BRING] = settings.thingsToBring.joinToString(LIST_DELIMITER)
    this[Keys.APPEARANCE] = settings.appearance.name
}

/**
 * The step the user should resume at.
 *
 * An unrecognised name falls back to [default] rather than throwing: the stored value is
 * the enum constant's own name, so renaming or removing a constant would otherwise make
 * an existing install unreadable.
 */
private fun Preferences.readOnboardingStep(default: OnboardingStep): OnboardingStep {
    this[Keys.ONBOARDING_STEP]?.let { raw ->
        runCatching { OnboardingStep.valueOf(raw) }.getOrNull()?.let { return it }
    }
    this[Keys.LEGACY_ONBOARDING_COMPLETE]?.let { finished ->
        return if (finished) OnboardingStep.COMPLETE else default
    }
    return default
}

/**
 * Stands in for "no reminder" on disk, since Preferences cannot hold a null Int. Chosen
 * outside both option lists, which are a rain percentage and a Celsius temperature the
 * user might plausibly pick.
 */
private const val REMINDER_OFF = Int.MIN_VALUE

/**
 * A reminder's cutoff, or null when the user switched that reminder off.
 *
 * A stored value outside [options] is treated as unusable and falls back to [default],
 * so settings can always show the stored cutoff as one of the choices it offers. Before
 * the cutoff was the user's to pick these were a plain on/off, so a leftover boolean
 * migrates: on takes the default cutoff, off means no reminder.
 */
private fun Preferences.readReminderThreshold(
    key: Preferences.Key<Int>,
    legacyKey: Preferences.Key<Boolean>,
    options: List<Int>,
    default: Int?,
): Int? {
    this[key]?.let { stored ->
        if (stored == REMINDER_OFF) return null
        return if (stored in options) stored else default
    }
    this[legacyKey]?.let { wanted -> return if (wanted) default else null }
    return default
}

private const val LEGACY_EARLIEST_FIELD = 3
private const val LEGACY_LATEST_FIELD = 4

/**
 * Pulls one time out of the retired wake window value, taken from its first window - the
 * only one a single global schedule can represent. Returns null when the key is absent
 * or its content no longer parses, so the caller falls through to the default.
 */
private fun Preferences.readLegacyWindowTime(field: Int): LocalTime? {
    val firstWindow = this[Keys.LEGACY_WAKE_WINDOWS]
        ?.split(LEGACY_WINDOW_DELIMITER)
        ?.firstOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    val fields = firstWindow.split(LEGACY_WINDOW_FIELD_DELIMITER)
    return fields.getOrNull(field)?.let { raw -> runCatching { LocalTime.parse(raw) }.getOrNull() }
}

private fun Preferences.readLocalTime(key: Preferences.Key<String>): LocalTime? =
    this[key]?.let { raw -> runCatching { LocalTime.parse(raw) }.getOrNull() }

private fun Preferences.readStringList(key: Preferences.Key<String>, default: List<String>): List<String> =
    this[key]?.let { raw ->
        if (raw.isEmpty()) emptyList() else raw.split(LIST_DELIMITER)
    } ?: default

package com.morningflow.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.morningflow.app.core.model.AppSettings
import com.morningflow.app.core.model.Appearance
import com.morningflow.app.core.model.DEFAULT_THINGS_TO_BRING
import com.morningflow.app.core.model.OnboardingStep
import com.morningflow.app.core.model.ReminderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalTime

/** Separators of the retired `wake_windows` value, kept here to build legacy fixtures. */
private const val LEGACY_WINDOW_RECORD = ""
private const val LEGACY_WINDOW_FIELD = ""
private const val LEGACY_DAY_SET = ""

/** One window in the retired format: id, label, days, earliest, latest. */
private fun legacyWindow(
    id: String,
    label: String,
    days: List<String>,
    earliest: String,
    latest: String,
) = listOf(id, label, days.joinToString(LEGACY_DAY_SET), earliest, latest)
    .joinToString(LEGACY_WINDOW_FIELD)

/**
 * DataStore Preferences' core API needs no Android Context, so most of this runs as a
 * plain JVM test (no Robolectric) backed by a real file in a temp directory.
 *
 * A handful of tests need a *second* write to the same file. On this Windows dev
 * machine, `androidx.datastore`'s Android-target FileStorage does that via plain
 * `File.renameTo`, which refuses to overwrite an existing destination on Windows (real
 * Android devices run Linux, where rename() overwrites atomically, so production code is
 * unaffected). Those specific tests exercise [DataStoreSettingsRepository]'s own
 * merge/compose logic rather than DataStore's file-replace mechanics, so they run against
 * [InMemoryPreferencesDataStore] instead — see the tests for details.
 */
class DataStoreSettingsRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Default + Job())
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun dataStoreAt(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun repositoryAt(file: File) = DataStoreSettingsRepository(dataStoreAt(file), scope)

    private fun repositoryWithInMemoryStore() =
        DataStoreSettingsRepository(InMemoryPreferencesDataStore(), scope)

    /**
     * Reads settings back out of a store seeded with raw keys, legacy ones included.
     *
     * [DataStoreSettingsRepository.awaitReady] is what makes this honest. The flow starts
     * at the built-in defaults, so reading it before the store has been read hands back
     * defaults whatever was seeded - which would silently pass every assertion here that
     * expects a fallback.
     */
    private suspend fun settingsStoredAs(build: MutablePreferences.() -> Unit): AppSettings {
        val store = InMemoryPreferencesDataStore()
        store.updateData { prefs -> prefs.toMutablePreferences().apply(build) }
        val repository = DataStoreSettingsRepository(store, scope)
        withTimeout(5_000) { repository.awaitReady() }
        return repository.settings.value
    }

    /**
     * What a settings screen does: wait, then read the stored value into its fields.
     * Reading `.value` without awaiting shows the defaults to someone who chose
     * something else, and saving from that screen writes the defaults back over it.
     */
    @Test
    fun awaitReady_makesTheStoredSettingsReadableFromValue() = runBlocking {
        val store = InMemoryPreferencesDataStore()
        store.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("earliest_wake_up")] = "06:15"
                this[stringPreferencesKey("ready_by_time")] = "08:05"
            }
        }

        val repository = DataStoreSettingsRepository(store, scope)
        withTimeout(5_000) { repository.awaitReady() }

        assertEquals(LocalTime.of(6, 15), repository.settings.value.schedule.earliestWakeUp)
        assertEquals(LocalTime.of(8, 5), repository.settings.value.schedule.readyBy)
    }

    /**
     * The signal has to be raised after the value is published, not before.
     *
     * This is a probe rather than a proof: the window between the two is a scheduling
     * detail, so repeating the construction only makes an out-of-order signal likely to
     * show up, not certain. The dependable guard is `settingsStoredAs` - every test
     * built on it reads `.value` the instant awaitReady returns, so raising the signal
     * early fails the suite even when this one happens to get away with it.
     */
    @Test
    fun awaitReady_doesNotReturnBeforeTheValueIsPublished() = runBlocking {
        repeat(25) { attempt ->
            val store = InMemoryPreferencesDataStore()
            store.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[stringPreferencesKey("earliest_wake_up")] = "06:15"
                }
            }

            val repository = DataStoreSettingsRepository(store, scope)
            withTimeout(5_000) { repository.awaitReady() }

            assertEquals(
                "attempt $attempt woke to the default instead of the stored value",
                LocalTime.of(6, 15),
                repository.settings.value.schedule.earliestWakeUp,
            )
        }
    }

    @Test
    fun awaitReady_returnsImmediatelyOnceLoaded() = runBlocking {
        val repository = repositoryWithInMemoryStore()
        withTimeout(5_000) { repository.awaitReady() }

        withTimeout(1_000) { repository.awaitReady() }
    }

    /** An unreadable store still has to settle, or every caller would hang on it. */
    @Test
    fun awaitReady_returnsOnACorruptedStore() = runBlocking {
        val file = File(tempFolder.root, "corrupted.preferences_pb")
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x42, 0x13, 0x37))
        val repository = repositoryAt(file)

        withTimeout(5_000) { repository.awaitReady() }

        assertEquals(OnboardingStep.WELCOME, repository.settings.value.onboardingStep)
    }

    @Test
    fun defaultsAreUsedWhenNothingHasBeenSaved() = runBlocking {
        val repository = repositoryAt(File(tempFolder.root, "settings1.preferences_pb"))

        val settings = repository.settings.first()

        assertEquals(OnboardingStep.WELCOME, settings.onboardingStep)
        assertEquals(false, settings.onboardingComplete)
        assertEquals(LocalTime.of(7, 0), settings.schedule.earliestWakeUp)
        assertEquals(LocalTime.of(7, 30), settings.schedule.latestWakeUp)
        assertEquals(LocalTime.of(9, 0), settings.schedule.readyBy)
        assertEquals(DEFAULT_THINGS_TO_BRING, settings.thingsToBring)
        assertEquals(0.3f, settings.audio.backgroundMusicVolume)
        assertEquals(0.3f, settings.audio.voiceGuidanceVolume)
        assertEquals(false, settings.reminders.sunscreen)
        assertEquals(40, settings.reminders.umbrellaRainPercent)
        assertEquals(8, settings.reminders.jacketTemperatureCelsius)
        assertEquals(true, settings.audio.voiceGuidanceEnabled)
    }

    @Test
    fun everyFieldRoundTripsThroughUpdateAndRead() = runBlocking {
        val repository = repositoryAt(File(tempFolder.root, "settings2.preferences_pb"))

        repository.update {
            it.copy(
                onboardingStep = OnboardingStep.COMPLETE,
                schedule = it.schedule.copy(
                    earliestWakeUp = LocalTime.of(6, 15),
                    latestWakeUp = LocalTime.of(6, 45),
                    readyBy = LocalTime.of(8, 30),
                ),
                audio = it.audio.copy(
                    backgroundMusicEnabled = false,
                    backgroundMusicVolume = 0.6f,
                    voiceGuidanceEnabled = false,
                    voiceGuidanceVolume = 0.4f,
                    audioCuesEnabled = false
                ),
                reminders = it.reminders.copy(
                    umbrellaRainPercent = 80,
                    jacketTemperatureCelsius = null,
                    sunscreen = true,
                ),
                thingsToBring = listOf("Umbrella", "Laptop")
            )
        }

        val settings = repository.settings.first { it.onboardingComplete }

        assertEquals(OnboardingStep.COMPLETE, settings.onboardingStep)
        assertEquals(LocalTime.of(6, 15), settings.schedule.earliestWakeUp)
        assertEquals(LocalTime.of(6, 45), settings.schedule.latestWakeUp)
        assertEquals(LocalTime.of(8, 30), settings.schedule.readyBy)
        assertEquals(false, settings.audio.backgroundMusicEnabled)
        assertEquals(0.6f, settings.audio.backgroundMusicVolume)
        assertEquals(0.4f, settings.audio.voiceGuidanceVolume)
        assertEquals(false, settings.audio.audioCuesEnabled)
        assertEquals(false, settings.audio.voiceGuidanceEnabled)
        assertEquals(80, settings.reminders.umbrellaRainPercent)
        assertNull(settings.reminders.jacketTemperatureCelsius)
        assertEquals(true, settings.reminders.sunscreen)
        assertEquals(listOf("Umbrella", "Laptop"), settings.thingsToBring)
    }

    @Test
    fun everyOnboardingStepRoundTrips() = runBlocking {
        val repository = repositoryWithInMemoryStore()

        OnboardingStep.entries.forEach { step ->
            repository.update { it.copy(onboardingStep = step) }
            assertEquals(step, repository.settings.first { it.onboardingStep == step }.onboardingStep)
        }
    }

    @Test
    fun emptyThingsToBringRoundTripsAsEmptyList() = runBlocking {
        // Two sequential writes to the same store: see the class doc for why this uses
        // the in-memory double instead of a real file-backed one.
        val repository = repositoryWithInMemoryStore()
        // Distinct sentinel value first: the default is already non-empty, so we need
        // something other than "non-empty" to prove this read reflects the update.
        repository.update { it.copy(thingsToBring = listOf("Wallet")) }
        repository.settings.first { it.thingsToBring == listOf("Wallet") }

        repository.update { it.copy(thingsToBring = emptyList()) }

        val settings = repository.settings.first { it.thingsToBring.isEmpty() }
        assertEquals(emptyList<String>(), settings.thingsToBring)
    }

    @Test
    fun appearanceDefaultsToTheSystemAndKeepsTheUsersChoice() = runBlocking {
        val repository = repositoryWithInMemoryStore()
        assertEquals(Appearance.SYSTEM, repository.settings.first().appearance)

        repository.update { it.copy(appearance = Appearance.DARK) }

        assertEquals(Appearance.DARK, repository.settings.first { it.appearance == Appearance.DARK }.appearance)
    }

    @Test
    fun updateOnlyChangesTargetedFieldsAndKeepsTheRest() = runBlocking {
        val repository = repositoryWithInMemoryStore()
        repository.update { it.copy(thingsToBring = listOf("Wallet")) }

        repository.update { it.copy(onboardingStep = OnboardingStep.COMPLETE) }

        val settings = repository.settings.first { it.onboardingComplete }
        assertEquals(listOf("Wallet"), settings.thingsToBring)
    }

    @Test
    fun concurrentUpdatesDoNotLoseWrites() = runBlocking {
        val repository = repositoryWithInMemoryStore()

        val jobs = (1..20).map { index ->
            scope.async {
                repository.update { it.copy(thingsToBring = it.thingsToBring + "item-$index") }
            }
        }
        jobs.awaitAll()

        val settings = repository.settings.first { it.thingsToBring.size >= 20 }
        assertEquals(20, settings.thingsToBring.count { it.startsWith("item-") })
    }

    @Test
    fun corruptedFileFallsBackToDefaultsInsteadOfCrashing() = runBlocking {
        val file = File(tempFolder.root, "settings6.preferences_pb")
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x42, 0x13, 0x37))

        val repository = repositoryAt(file)
        val settings = repository.settings.first()

        assertEquals(LocalTime.of(7, 0), settings.schedule.earliestWakeUp)
        assertEquals(LocalTime.of(7, 30), settings.schedule.latestWakeUp)
        assertEquals(OnboardingStep.WELCOME, settings.onboardingStep)
    }

    @Test
    fun aMalformedTimeFallsBackToItsDefault() = runBlocking {
        val settings = settingsStoredAs {
            this[stringPreferencesKey("earliest_wake_up")] = "half past six"
            this[stringPreferencesKey("latest_wake_up")] = "07:45"
        }

        assertEquals(LocalTime.of(7, 0), settings.schedule.earliestWakeUp)
        // The neighbouring key is still honoured: one bad value doesn't take the rest down.
        assertEquals(LocalTime.of(7, 45), settings.schedule.latestWakeUp)
    }

    /**
     * An onboarding step is stored by enum name, so a name this build no longer knows -
     * a step that was renamed or removed - must not make the whole settings read fail.
     */
    @Test
    fun anUnknownOnboardingStepNameFallsBackToTheFirstStep() = runBlocking {
        val settings = settingsStoredAs {
            this[stringPreferencesKey("onboarding_step")] = "WAKE_UP_SETUP_V2"
        }

        assertEquals(OnboardingStep.WELCOME, settings.onboardingStep)
    }

    @Test
    fun aReminderSwitchedOffRoundTripsAsOff() = runBlocking {
        val repository = repositoryWithInMemoryStore()

        repository.update {
            it.copy(
                reminders = it.reminders.copy(
                    umbrellaRainPercent = null,
                    jacketTemperatureCelsius = null,
                ),
            )
        }

        val settings = repository.settings.first { it.reminders.umbrellaRainPercent == null }
        assertNull(settings.reminders.jacketTemperatureCelsius)
    }

    @Test
    fun everyOfferedCutoffRoundTrips() = runBlocking {
        val repository = repositoryWithInMemoryStore()

        ReminderPreferences.UMBRELLA_RAIN_PERCENT_OPTIONS.forEach { percent ->
            repository.update { it.copy(reminders = it.reminders.copy(umbrellaRainPercent = percent)) }
            val settings = repository.settings.first { it.reminders.umbrellaRainPercent == percent }
            assertEquals(percent, settings.reminders.umbrellaRainPercent)
        }
        ReminderPreferences.JACKET_TEMPERATURE_OPTIONS.forEach { celsius ->
            repository.update {
                it.copy(reminders = it.reminders.copy(jacketTemperatureCelsius = celsius))
            }
            val settings = repository.settings.first { it.reminders.jacketTemperatureCelsius == celsius }
            assertEquals(celsius, settings.reminders.jacketTemperatureCelsius)
        }
    }

    /**
     * Settings shows the stored cutoff as one of the choices it offers, so a value that
     * is not one of them - written by another build - has to fall back rather than leave
     * the control with nothing selected.
     */
    @Test
    fun aCutoffOutsideTheOfferedOptionsFallsBackToTheDefault() = runBlocking {
        val settings = settingsStoredAs {
            this[intPreferencesKey("reminder_umbrella_rain_percent")] = 37
            this[intPreferencesKey("reminder_jacket_temperature_celsius")] = 99
        }

        assertEquals(
            ReminderPreferences.DEFAULT_UMBRELLA_RAIN_PERCENT,
            settings.reminders.umbrellaRainPercent,
        )
        assertEquals(
            ReminderPreferences.DEFAULT_JACKET_TEMPERATURE_CELSIUS,
            settings.reminders.jacketTemperatureCelsius,
        )
    }

    // ----- migration off the retired keys -----

    @Test
    fun aLegacyReminderLeftOnTakesTheDefaultCutoff() = runBlocking {
        val settings = settingsStoredAs {
            this[booleanPreferencesKey("reminder_umbrella")] = true
            this[booleanPreferencesKey("reminder_jacket")] = true
        }

        assertEquals(
            ReminderPreferences.DEFAULT_UMBRELLA_RAIN_PERCENT,
            settings.reminders.umbrellaRainPercent,
        )
        assertEquals(
            ReminderPreferences.DEFAULT_JACKET_TEMPERATURE_CELSIUS,
            settings.reminders.jacketTemperatureCelsius,
        )
    }

    @Test
    fun aLegacyReminderSwitchedOffStaysOff() = runBlocking {
        val settings = settingsStoredAs {
            this[booleanPreferencesKey("reminder_umbrella")] = false
            this[booleanPreferencesKey("reminder_jacket")] = false
        }

        assertNull(settings.reminders.umbrellaRainPercent)
        assertNull(settings.reminders.jacketTemperatureCelsius)
    }

    @Test
    fun aStoredCutoffWinsOverTheLegacyToggle() = runBlocking {
        val settings = settingsStoredAs {
            this[booleanPreferencesKey("reminder_umbrella")] = false
            this[intPreferencesKey("reminder_umbrella_rain_percent")] = 80
        }

        assertEquals(80, settings.reminders.umbrellaRainPercent)
    }

    @Test
    fun aFinishedLegacyOnboardingResumesAsComplete() = runBlocking {
        val settings = settingsStoredAs {
            this[booleanPreferencesKey("onboarding_complete")] = true
        }

        assertEquals(OnboardingStep.COMPLETE, settings.onboardingStep)
        assertEquals(true, settings.onboardingComplete)
    }

    @Test
    fun anUnfinishedLegacyOnboardingRestartsAtTheFirstStep() = runBlocking {
        val settings = settingsStoredAs {
            this[booleanPreferencesKey("onboarding_complete")] = false
        }

        assertEquals(OnboardingStep.WELCOME, settings.onboardingStep)
    }

    @Test
    fun aStoredOnboardingStepWinsOverTheLegacyFlag() = runBlocking {
        val settings = settingsStoredAs {
            this[booleanPreferencesKey("onboarding_complete")] = true
            this[stringPreferencesKey("onboarding_step")] = OnboardingStep.PERMISSIONS.name
        }

        assertEquals(OnboardingStep.PERMISSIONS, settings.onboardingStep)
    }

    /**
     * The retired value carried per-window days. Detection is no longer day-scoped, so the
     * migration keeps only the first window's times and drops its days.
     */
    @Test
    fun legacyWakeWindowsMigrateToTheGlobalScheduleTimes() = runBlocking {
        val settings = settingsStoredAs {
            this[stringPreferencesKey("wake_windows")] = legacyWindow(
                id = "window-1",
                label = "Weekdays",
                days = listOf("MONDAY", "TUESDAY"),
                earliest = "06:15",
                latest = "06:45",
            )
        }

        assertEquals(LocalTime.of(6, 15), settings.schedule.earliestWakeUp)
        assertEquals(LocalTime.of(6, 45), settings.schedule.latestWakeUp)
    }

    /** A single global schedule cannot hold two windows, so the first one wins. */
    @Test
    fun onlyTheFirstLegacyWakeWindowIsMigrated() = runBlocking {
        val settings = settingsStoredAs {
            this[stringPreferencesKey("wake_windows")] = listOf(
                legacyWindow("weekdays", "Weekdays", listOf("MONDAY"), "06:15", "06:45"),
                legacyWindow("weekend", "Weekend", listOf("SATURDAY"), "08:00", "08:45"),
            ).joinToString(LEGACY_WINDOW_RECORD)
        }

        assertEquals(LocalTime.of(6, 15), settings.schedule.earliestWakeUp)
        assertEquals(LocalTime.of(6, 45), settings.schedule.latestWakeUp)
    }

    @Test
    fun aNewlyWrittenScheduleWinsOverLegacyWakeWindows() = runBlocking {
        val settings = settingsStoredAs {
            this[stringPreferencesKey("wake_windows")] =
                legacyWindow("weekdays", "Weekdays", listOf("MONDAY"), "06:15", "06:45")
            this[stringPreferencesKey("earliest_wake_up")] = "05:30"
            this[stringPreferencesKey("latest_wake_up")] = "05:50"
        }

        assertEquals(LocalTime.of(5, 30), settings.schedule.earliestWakeUp)
        assertEquals(LocalTime.of(5, 50), settings.schedule.latestWakeUp)
    }

    @Test
    fun anUnreadableLegacyWakeWindowFallsBackToTheDefaultSchedule() = runBlocking {
        val settings = settingsStoredAs {
            this[stringPreferencesKey("wake_windows")] = "not-a-window"
        }

        val defaults = AppSettings().schedule
        assertEquals(defaults.earliestWakeUp, settings.schedule.earliestWakeUp)
        assertEquals(defaults.latestWakeUp, settings.schedule.latestWakeUp)
    }

    /** Wake-up watching used to be switched off by storing no windows at all. */
    @Test
    fun anEmptyLegacyWakeWindowValueFallsBackToTheDefaultSchedule() = runBlocking {
        val settings = settingsStoredAs {
            this[stringPreferencesKey("wake_windows")] = ""
        }

        val defaults = AppSettings().schedule
        assertEquals(defaults.earliestWakeUp, settings.schedule.earliestWakeUp)
        assertEquals(defaults.latestWakeUp, settings.schedule.latestWakeUp)
    }

    /** ready_by_time kept its key across the change, so it needs no migration. */
    @Test
    fun theReadyByKeyIsReadUnchangedFromBeforeTheScheduleExisted() = runBlocking {
        val settings = settingsStoredAs {
            this[stringPreferencesKey("ready_by_time")] = "08:05"
        }

        assertEquals(LocalTime.of(8, 5), settings.schedule.readyBy)
    }
}

/**
 * Mirrors real DataStore's actor semantics (only one `updateData` transform runs at a
 * time) without touching the filesystem, so tests that need several sequential writes
 * to one store aren't tripped up by this machine's Windows file-replace limitation.
 */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
}

package com.morningflow.app.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.morningflow.app.core.data.session.DataStoreSessionSnapshotStore
import com.morningflow.app.core.data.session.SessionSnapshotStore
import com.morningflow.app.core.audio.AndroidAudioController
import com.morningflow.app.core.audio.AudioController
import com.morningflow.app.core.data.calendar.impl.AndroidCalendarRepository
import com.morningflow.app.core.data.calendar.impl.CalendarRepositoryAdapter
import com.morningflow.app.core.data.location.impl.AndroidLocationSource
import com.morningflow.app.core.data.location.impl.AndroidReverseGeocoder
import com.morningflow.app.core.data.location.impl.LocationDataStore
import com.morningflow.app.core.data.location.impl.LocationRepositoryImpl
import com.morningflow.app.core.data.location.impl.ManualCityRepository
import com.morningflow.app.core.data.notification.api.MorningPromptNotificationSettingsIntentProvider
import com.morningflow.app.core.data.notification.api.MorningPromptNotifier
import com.morningflow.app.core.data.notification.api.NotificationAvailabilityProvider
import com.morningflow.app.core.data.notification.impl.AndroidMorningPromptNotificationSettingsIntentProvider
import com.morningflow.app.core.data.notification.impl.AndroidMorningPromptNotifier
import com.morningflow.app.core.data.notification.impl.AndroidNotificationAvailabilityProvider
import com.morningflow.app.core.data.weather.impl.OpenMeteoApiFactory
import com.morningflow.app.core.data.weather.impl.OpenMeteoWeatherRepository
import com.morningflow.app.core.data.weather.impl.WeatherDataStore
import com.morningflow.app.core.data.weather.impl.WeatherInfoRepositoryAdapter
import com.morningflow.app.core.database.MorningFlowDatabase
import com.morningflow.app.core.datastore.DataStoreSettingsRepository
import com.morningflow.app.core.domain.weatherlocation.WeatherLocationCoordinator
import com.morningflow.app.core.domain.weatherlocation.WeatherLocationRefreshUseCase
import com.morningflow.app.core.permissions.AndroidExactAlarmAccessChecker
import com.morningflow.app.core.permissions.ExactAlarmAccessChecker
import com.morningflow.app.core.wakeup.AndroidWakeDetectionRuntimeController
import com.morningflow.app.core.wakeup.AndroidWakeupScheduler
import com.morningflow.app.core.wakeup.WakeDetectionRuntimeController
import com.morningflow.app.core.wakeup.WakeupScheduleCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Which implementations [AppContainer] binds.
 *
 * Everything is real by default, which is what ships. Each flag swaps in the in-memory
 * stand-in for one capability, so a screen can be exercised on hardware that cannot
 * actually produce the input - an emulator has no step counter or light sensor, and its
 * location and calendar are empty.
 *
 * Persistence has no flag. Templates, settings and session history are the thing being
 * verified on a device, so substituting them would make the run meaningless.
 */
data class ContainerConfig(
    val useFakeSensors: Boolean = false,
    val useFakeWakeUpDetection: Boolean = false,
    val useFakeWeather: Boolean = false,
    val useFakeCalendar: Boolean = false,
) {
    companion object {
        /** What ships. */
        val REAL = ContainerConfig()

        /** Everything an emulator cannot do for real, stood in for. */
        val EMULATOR = ContainerConfig(
            useFakeSensors = true,
            useFakeWakeUpDetection = true,
            useFakeWeather = true,
            useFakeCalendar = true,
        )
    }
}

/**
 * The single place interfaces are bound to implementations.
 *
 * Templates, settings, session history, audio, sensors, weather-location and Calendar
 * are backed by their real implementations. Compatibility adapters keep the legacy
 * SessionEngine contracts separate from feature-owned repositories.
 *
 * Pass a [config] to substitute a capability - see [ContainerConfig.EMULATOR].
 */
class AppContainer(
    context: Context,
    private val config: ContainerConfig = ContainerConfig.REAL,
) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: MorningFlowDatabase = MorningFlowDatabase.build(appContext)

    private val settingsDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = appScope,
        produceFile = { appContext.preferencesDataStoreFile("settings") },
    )
    private val locationDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = appScope,
        produceFile = { appContext.preferencesDataStoreFile("location") },
    )
    private val manualCityDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = appScope,
        produceFile = { appContext.preferencesDataStoreFile("manual_city") },
    )
    private val weatherDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = appScope,
        produceFile = { appContext.preferencesDataStoreFile("weather") },
    )

    private val sessionSnapshotDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = appScope,
        produceFile = { appContext.preferencesDataStoreFile("session_snapshot") },
    )

    val templateRepository: TemplateRepository =
        RoomTemplateRepository(database.templateDao(), appScope)
    val settingsRepository: SettingsRepository =
        DataStoreSettingsRepository(settingsDataStore, appScope)
    val sessionRecordRepository: SessionRecordRepository =
        RoomSessionRecordRepository(database.sessionRecordDao(), appScope)
    val audioController: AudioController = AndroidAudioController(appContext)
    val sessionSnapshotStore: SessionSnapshotStore = DataStoreSessionSnapshotStore(sessionSnapshotDataStore)

    val manualCityRepository = ManualCityRepository(
        api = ManualCityRepository.createApi(),
        store = LocationDataStore(manualCityDataStore),
    )
    private val locationSource = AndroidLocationSource(appContext)
    private val reverseGeocoder = AndroidReverseGeocoder(appContext)
    val locationRepository: com.morningflow.app.core.data.location.api.LocationRepository =
        LocationRepositoryImpl(
            source = locationSource,
            reverseGeocoder = reverseGeocoder,
            dataStore = LocationDataStore(locationDataStore),
            manualCity = { manualCityRepository.load() },
        )
    val weatherLocationRepository: com.morningflow.app.core.data.weather.api.WeatherRepository =
        OpenMeteoWeatherRepository(
            api = OpenMeteoApiFactory.create(),
            dataStore = WeatherDataStore(weatherDataStore),
        )
    val weatherLocationCoordinator: WeatherLocationCoordinator = WeatherLocationCoordinator(
        refreshUseCase = WeatherLocationRefreshUseCase(
            locationRepository = locationRepository,
            weatherRepository = weatherLocationRepository,
        ),
    )

    val weatherRepository: WeatherRepository =
        if (config.useFakeWeather) {
            FakeWeatherRepository()
        } else {
            WeatherInfoRepositoryAdapter(
                locationRepository = locationRepository,
                weatherRepository = weatherLocationRepository,
                maxWeatherAgeMillis = {
                    weatherLocationCoordinator.refreshIntervalMinutes * 60_000L
                },
            )
        }
    private val calendarFeatureRepository = AndroidCalendarRepository(appContext)
    val calendarRepository: CalendarRepository =
        if (config.useFakeCalendar) {
            FakeCalendarRepository()
        } else {
            CalendarRepositoryAdapter(delegate = calendarFeatureRepository)
        }

    // Steps, squats and ambient light. On a device missing one of these the counts stay
    // at 0 and the session falls back to timed completion rather than stalling.
    val sensorRepository: SensorRepository =
        if (config.useFakeSensors) FakeSensorRepository() else RealSensorRepository(appContext)

    // Wake-up detection, separate from the in-session sensors above. It never starts on
    // its own: the wake-up scheduler calls start()/stop()/reset(). The fake exposes
    // simulateWakeUp() so the prompt can be triggered by hand.
    val wakeUpDetection: WakeUpDetection =
        if (config.useFakeWakeUpDetection) FakeWakeUpDetection() else RealWakeUpDetection(appContext)
    val notificationAvailability: NotificationAvailabilityProvider =
        AndroidNotificationAvailabilityProvider(appContext)
    val morningPromptNotificationSettingsIntentProvider: MorningPromptNotificationSettingsIntentProvider =
        AndroidMorningPromptNotificationSettingsIntentProvider(appContext)
    val morningPromptNotifier: MorningPromptNotifier = AndroidMorningPromptNotifier(
        context = appContext,
        availabilityProvider = notificationAvailability,
    )
    val exactAlarmAccessChecker: ExactAlarmAccessChecker = AndroidExactAlarmAccessChecker(appContext)
    val wakeDetectionRuntimeController: WakeDetectionRuntimeController =
        AndroidWakeDetectionRuntimeController(appContext)
    val wakeupScheduler: WakeupScheduler = AndroidWakeupScheduler(
        context = appContext,
        exactAlarmAccessChecker = exactAlarmAccessChecker,
    )
    val wakeupScheduleCoordinator = WakeupScheduleCoordinator(
        settingsRepository = settingsRepository,
        scheduler = wakeupScheduler,
    )
}

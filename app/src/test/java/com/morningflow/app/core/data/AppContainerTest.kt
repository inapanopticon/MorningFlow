package com.morningflow.app.core.data

import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.app.MorningFlowApplication
import com.morningflow.app.core.data.calendar.impl.CalendarRepositoryAdapter
import com.morningflow.app.core.data.location.impl.LocationRepositoryImpl
import com.morningflow.app.core.data.notification.impl.AndroidMorningPromptNotificationSettingsIntentProvider
import com.morningflow.app.core.data.notification.impl.AndroidMorningPromptNotifier
import com.morningflow.app.core.data.notification.impl.AndroidNotificationAvailabilityProvider
import com.morningflow.app.core.data.weather.impl.OpenMeteoWeatherRepository
import com.morningflow.app.core.data.weather.impl.WeatherInfoRepositoryAdapter
import com.morningflow.app.core.permissions.AndroidExactAlarmAccessChecker
import com.morningflow.app.core.wakeup.AndroidWakeDetectionRuntimeController
import com.morningflow.app.core.wakeup.AndroidWakeupScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppContainerTest {
    /**
     * The application has already built the one container, and its DataStores and
     * database must stay single per process. A second container here would open the same
     * files again and fail in the background.
     */
    private fun applicationContainer(): AppContainer =
        ApplicationProvider.getApplicationContext<MorningFlowApplication>().appContainer

    @Test
    fun weatherLocationDependenciesUseProductionImplementations() {
        val container = applicationContainer()

        assertTrue(container.locationRepository is LocationRepositoryImpl)
        assertTrue(container.weatherLocationRepository is OpenMeteoWeatherRepository)
        assertTrue(container.weatherRepository is WeatherInfoRepositoryAdapter)
        assertTrue(container.calendarRepository is CalendarRepositoryAdapter)
        assertEquals(15L, container.weatherLocationCoordinator.refreshIntervalMinutes)
    }

    @Test
    fun wakeupDependenciesUseProductionImplementations() {
        val container = applicationContainer()

        assertTrue(container.exactAlarmAccessChecker is AndroidExactAlarmAccessChecker)
        assertTrue(
            container.wakeDetectionRuntimeController is AndroidWakeDetectionRuntimeController,
        )
        assertTrue(container.wakeupScheduler is AndroidWakeupScheduler)
    }

    @Test
    fun notificationDependenciesUseProductionImplementations() {
        val container = applicationContainer()

        assertTrue(container.notificationAvailability is AndroidNotificationAvailabilityProvider)
        assertTrue(
            container.morningPromptNotificationSettingsIntentProvider is
                AndroidMorningPromptNotificationSettingsIntentProvider,
        )
        assertTrue(container.morningPromptNotifier is AndroidMorningPromptNotifier)
    }
}

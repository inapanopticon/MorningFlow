package com.morningflow.app.core.permissions

import android.app.AlarmManager
import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
class AndroidExactAlarmAccessCheckerTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val alarmManager =
        application.getSystemService(AlarmManager::class.java)
            ?: error("AlarmManager is unavailable in the test environment")

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    @Test
    fun preAndroid12IsAvailableEvenWhenThePlatformShadowIsDenied() {
        val checker = AndroidExactAlarmAccessChecker(
            alarmManager = alarmManager,
            sdkInt = Build.VERSION_CODES.R,
        )

        assertTrue(checker.hasExactAlarmAccess())
    }

    @Test
    fun Android12AndLaterReportsDeniedPlatformCapability() {
        val checker = AndroidExactAlarmAccessChecker(
            alarmManager = alarmManager,
            sdkInt = Build.VERSION_CODES.S,
        )

        assertFalse(checker.hasExactAlarmAccess())
    }

    @Test
    fun Android12AndLaterReportsGrantedPlatformCapability() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val checker = AndroidExactAlarmAccessChecker(
            alarmManager = alarmManager,
            sdkInt = Build.VERSION_CODES.S,
        )

        assertTrue(checker.hasExactAlarmAccess())
    }
}

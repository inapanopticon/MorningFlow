package com.morningflow.app.core.wakeup

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WakeupRecoveryReceiverTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun systemRecoveryActionsRequestOneReconcileEach() {
        var requestCount = 0
        val receiver = WakeupRecoveryReceiver {
            requestCount += 1
            Job().apply { complete() }
        }

        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        actions.forEach { action ->
            assertNotNull(receiver.recoveryJob(application, action))
        }

        assertEquals(actions.size, requestCount)
    }

    @Test
    fun unrelatedOrMissingActionIsIgnored() {
        var requestCount = 0
        val receiver = WakeupRecoveryReceiver {
            requestCount += 1
            Job().apply { complete() }
        }

        assertNull(receiver.recoveryJob(application, "com.morningflow.UNRELATED"))
        assertNull(receiver.recoveryJob(application, null))

        assertEquals(0, requestCount)
    }

    @Test
    fun manifestKeepsRecoveryReceiverPrivateAndDeclaresBootPermission() {
        val packageManager = application.packageManager
        val receiverInfo = packageManager.getReceiverInfo(
            ComponentName(application, WakeupRecoveryReceiver::class.java),
            0,
        )
        val packageInfo = packageManager.getPackageInfo(application.packageName, 0x00001000)

        assertFalse(receiverInfo.exported)
        assertTrue(
            packageInfo.requestedPermissions.orEmpty()
                .contains(Manifest.permission.RECEIVE_BOOT_COMPLETED),
        )
        assertNotNull(WakeupRecoveryReceiver::class.java.getDeclaredConstructor().newInstance())
    }
}

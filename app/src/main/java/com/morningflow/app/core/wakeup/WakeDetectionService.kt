package com.morningflow.app.core.wakeup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.morningflow.app.R
import com.morningflow.app.app.MorningFlowApplication
import com.morningflow.app.core.data.WakeUpDetection
import com.morningflow.app.core.data.notification.api.MorningPromptNotifier
import com.morningflow.app.core.data.notification.api.NotificationPostResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Runs wake-up detection only for the Alarm snapshot's [Earliest, Latest) window. */
open class WakeDetectionService : Service() {
    private lateinit var serviceScope: CoroutineScope
    private var sessionJob: Job? = null
    private var activeDetection: WakeUpDetection? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope = CoroutineScope(SupervisorJob() + serviceDispatcher())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!promoteToForeground()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Every new command replaces any previous run, including malformed commands.
        // This prevents an old detector from continuing after foreground state is removed.
        stopActiveSession()
        val snapshot = intent
            ?.takeIf { it.action == ACTION_RUN_DETECTION }
            ?.let(WakeAlarmContract::readSnapshot)
        val clock = serviceClock()
        val startedAt = clock.instant()
        if (snapshot == null || !snapshot.contains(startedAt, serviceZoneId(clock))) {
            finishForeground(startId)
            return START_NOT_STICKY
        }

        val detection = createWakeUpDetection()
        if (detection == null) {
            // A non-MorningFlow host or an incomplete composition root cannot run detection.
            finishForeground(startId)
            return START_NOT_STICKY
        }

        val started = try {
            detection.reset()
            detection.start()
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to start wake detection", exception)
            false
        }
        if (!started) {
            safeStop(detection)
            finishForeground(startId)
            return START_NOT_STICKY
        }
        activeDetection = detection

        val deadlineMillis = snapshot.latestInstant(startedAt, serviceZoneId(clock))
            .toEpochMilli() - startedAt.toEpochMilli()
        if (deadlineMillis <= 0L) {
            finishSession(startId, detection)
            return START_NOT_STICKY
        }

        // The detector subscribes to the non-wakeup sensor variants: once the screen is
        // off and the SoC suspends, their events stop arriving even though this
        // foreground Service stays alive. A partial wake lock bounded by the detection
        // deadline keeps the CPU - and with it the event stream - running.
        wakeLock = acquireDetectionWakeLock(deadlineMillis + WAKE_LOCK_GRACE_MILLIS)

        sessionJob = serviceScope.launch {
            val detectedAt = withTimeoutOrNull(deadlineMillis) {
                detection.state.first { it.detectedAt != null }.detectedAt
            }
            try {
                if (detectedAt != null) {
                    onWakeUpDetected(detectedAt)
                }
            } finally {
                finishSession(startId, detection)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        sessionJob?.cancel()
        serviceScope.cancel()
        stopActiveSession()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Resolves the process-wide detector through the application's composition root.
     *
     * The manifest keeps this Service in the default app process, so Android creates
     * [MorningFlowApplication] before the Service and both components share one AppContainer.
     * Tests override this seam with [com.morningflow.app.core.data.FakeWakeUpDetection].
     */
    protected open fun createWakeUpDetection(): WakeUpDetection? =
        (application as? MorningFlowApplication)?.appContainer?.wakeUpDetection

    /** Resolves the product prompt boundary without owning notification infrastructure. */
    protected open fun createMorningPromptNotifier(): MorningPromptNotifier? =
        (application as? MorningFlowApplication)?.appContainer?.morningPromptNotifier

    protected open fun serviceClock(): Clock = Clock.systemDefaultZone()

    protected open fun serviceZoneId(clock: Clock): ZoneId = clock.zone

    protected open fun serviceDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    /** Requests at most one product prompt for this Service run's first valid detection. */
    protected open fun onWakeUpDetected(detectedAt: Instant) {
        val result = try {
            createMorningPromptNotifier()?.post()
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Wake-up prompt request threw at $detectedAt", exception)
            null
        }
        when (result) {
            NotificationPostResult.Posted -> Unit
            is NotificationPostResult.Unavailable ->
                Log.w(TAG, "Wake-up prompt unavailable at $detectedAt: ${result.reason}")
            NotificationPostResult.PlatformRejected ->
                Log.w(TAG, "Wake-up prompt rejected by the platform at $detectedAt")
            null -> Log.w(TAG, "Wake-up prompt notifier unavailable at $detectedAt")
        }
    }

    private fun finishSession(startId: Int, detection: WakeUpDetection) {
        if (activeDetection !== detection) return
        safeStop(detection)
        activeDetection = null
        sessionJob = null
        releaseWakeLock()
        finishForeground(startId)
    }

    private fun stopActiveSession() {
        sessionJob?.cancel()
        sessionJob = null
        activeDetection?.let(::safeStop)
        activeDetection = null
        releaseWakeLock()
    }

    /** Overridable so tests can observe or substitute the platform wake lock. */
    protected open fun acquireDetectionWakeLock(timeoutMillis: Long): PowerManager.WakeLock? = try {
        (getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply {
                setReferenceCounted(false)
                acquire(timeoutMillis)
            }
    } catch (exception: RuntimeException) {
        Log.e(TAG, "Unable to acquire detection wake lock", exception)
        null
    }

    private fun releaseWakeLock() {
        val held = wakeLock ?: return
        wakeLock = null
        try {
            if (held.isHeld) held.release()
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to release detection wake lock", exception)
        }
    }

    private fun safeStop(detection: WakeUpDetection) {
        try {
            detection.stop()
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to stop wake detection", exception)
        }
    }

    private fun finishForeground(startId: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }

    private fun promoteToForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        true
    } catch (exception: RuntimeException) {
        Log.e(TAG, "Unable to promote wake detection to foreground", exception)
        false
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_detection_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.wake_detection_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_sun)
        .setContentTitle(getString(R.string.wake_detection_notification_title))
        .setContentText(getString(R.string.wake_detection_notification_text))
        .setCategory(Notification.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setSilent(true)
        .build()

    internal companion object {
        const val ACTION_RUN_DETECTION = "com.morningflow.app.action.RUN_WAKE_DETECTION"
        const val CHANNEL_ID = "wake_detection_service"
        const val NOTIFICATION_ID = 0x5747
        const val TAG = "WakeDetectionService"
        const val WAKE_LOCK_TAG = "MorningFlow:WakeDetection"
        private const val WAKE_LOCK_GRACE_MILLIS = 5_000L

        fun startIntent(context: Context, snapshot: WakeScheduleSnapshot): Intent =
            WakeAlarmContract.putSnapshot(
                Intent(context, WakeDetectionService::class.java)
                    .setAction(ACTION_RUN_DETECTION),
                snapshot,
            )
    }
}

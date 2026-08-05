package com.dgraciano.breathe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dgraciano.breathe.R
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.ui.pause.PauseActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class AppMonitorService : Service() {

    @Inject lateinit var detector: ForegroundAppDetector
    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var sessionApprovalStore: SessionApprovalStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastForeground: String? = null
    private lateinit var powerManager: PowerManager

    /**
     * The detector reports the foreground app continuously, so between startActivity()
     * and PauseActivity actually resuming the loop would otherwise re-launch the pause
     * screen several times for the same app.
     */
    private var pauseLaunchedFor: String? = null
    private var pauseLaunchedAt = 0L

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        startForeground(NOTIF_ID, buildNotification())
        startMonitoring()
        Log.d("BreatheService", "Service created and monitoring started")
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                if (powerManager.isInteractive) {
                    val current = detector.getCurrentApp()

                    // If we switched apps, reset the approved session
                    if (current != null && current != packageName) {
                        if (current != lastForeground) {
                            Log.d("BreatheService", "Foreground app changed: $current")
                            // We ONLY remove the session when moving away
                            sessionApprovalStore.revoke(lastForeground)
                            lastForeground = current
                            // Moving to a different app makes any pending launch stale.
                            if (current != pauseLaunchedFor) pauseLaunchedFor = null
                        }

                        // Only block if the session hasn't been approved via the pause screen
                        if (!sessionApprovalStore.isApproved(current)) {
                            if (appRepository.isBlocked(current) && shouldLaunchPause(current)) {
                                Log.d("BreatheService", "Blocking app: $current")
                                if (Settings.canDrawOverlays(this@AppMonitorService)) {
                                    // Approval only happens when the user taps "YES" in PauseActivity
                                    pauseLaunchedFor = current
                                    pauseLaunchedAt = System.currentTimeMillis()
                                    launchPause(current)
                                }
                            }
                        }
                    }
                }
                delay(500.milliseconds)
            }
        }
    }

    private fun shouldLaunchPause(packageName: String): Boolean =
        packageName != pauseLaunchedFor ||
                System.currentTimeMillis() - pauseLaunchedAt > PAUSE_RELAUNCH_DEBOUNCE_MS

    private fun launchPause(packageName: String) {
        val intent = PauseActivity.newIntent(this, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "breathe_monitor"
        private const val PAUSE_RELAUNCH_DEBOUNCE_MS = 3_000L

        fun start(context: Context) =
            context.startForegroundService(Intent(context, AppMonitorService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, AppMonitorService::class.java))
    }
}

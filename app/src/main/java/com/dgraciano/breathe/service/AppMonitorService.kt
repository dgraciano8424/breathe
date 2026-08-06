package com.dgraciano.breathe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dgraciano.breathe.R
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.ui.pause.PauseActivity
import com.dgraciano.breathe.ui.pause.PauseOverlayHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class AppMonitorService : Service() {

    @Inject lateinit var detector: ForegroundAppDetector
    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var sessionApprovalStore: SessionApprovalStore
    @Inject lateinit var pauseOverlayHost: PauseOverlayHost

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

    /**
     * Mirrors the blocked table in memory. The loop runs twice a second, and a Room
     * `EXISTS` query at that rate is pure overhead when the answer almost never changes.
     */
    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        startForeground(NOTIF_ID, buildNotification())
        observeBlockedApps()
        startMonitoring()
        Log.d("BreatheService", "Service created and monitoring started")
    }

    /**
     * Idempotent: the system re-delivers the start command on restart, and the user can
     * hit "start monitoring" repeatedly. START_STICKY because this service is the
     * product — if it is killed, it should come back without waiting for the user.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun observeBlockedApps() {
        scope.launch {
            appRepository.getBlockedApps().collect { apps ->
                blockedPackages = apps.map { it.packageName }.toSet()
            }
        }
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                // The overlay draws over the blocked app without displacing it, so the
                // detector keeps reporting that app while the pause screen is up.
                val idle = !powerManager.isInteractive ||
                        pauseOverlayHost.isShowing ||
                        blockedPackages.isEmpty()

                if (idle) {
                    // Nothing to detect: back off hard rather than spinning the usage
                    // query while the screen is off or no apps are being monitored.
                    delay(IDLE_POLL_INTERVAL)
                    continue
                }

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
                    if (!sessionApprovalStore.isApproved(current) &&
                        current in blockedPackages &&
                        shouldLaunchPause(current)
                    ) {
                        Log.d("BreatheService", "Blocking app: $current")
                        // Approval only happens when the user taps "YES".
                        pauseLaunchedFor = current
                        pauseLaunchedAt = System.currentTimeMillis()
                        launchPause(current)
                    }
                }
                delay(ACTIVE_POLL_INTERVAL)
            }
        }
    }

    private fun shouldLaunchPause(packageName: String): Boolean =
        packageName != pauseLaunchedFor ||
                System.currentTimeMillis() - pauseLaunchedAt > PAUSE_RELAUNCH_DEBOUNCE_MS

    /**
     * Prefers the overlay window. Starting an Activity from here is unreliable on
     * Android 10+, so it is only a fallback for when overlay permission is missing —
     * the pause screen may well not appear in that case, which is why onboarding asks
     * for the permission up front.
     */
    private fun launchPause(packageName: String) {
        if (pauseOverlayHost.canShow()) {
            val appName = resolveAppName(packageName)
            pauseOverlayHost.show(packageName, appName)
            return
        }

        Log.w("BreatheService", "No overlay permission; attempting activity launch")
        val intent = PauseActivity.newIntent(this, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(intent)
    }

    private fun resolveAppName(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

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
        // The overlay is a window, not an Activity — nothing else would take it down.
        pauseOverlayHost.hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "breathe_monitor"
        private const val PAUSE_RELAUNCH_DEBOUNCE_MS = 3_000L
        private val ACTIVE_POLL_INTERVAL = 500.milliseconds
        private val IDLE_POLL_INTERVAL = 3.seconds

        fun start(context: Context) =
            context.startForegroundService(Intent(context, AppMonitorService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, AppMonitorService::class.java))
    }
}

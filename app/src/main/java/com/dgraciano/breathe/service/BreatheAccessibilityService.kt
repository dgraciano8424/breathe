package com.dgraciano.breathe.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.app.KeyguardManager
import android.os.PowerManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.dgraciano.breathe.MainActivity
import android.provider.Settings
import android.util.Log
import android.content.pm.ApplicationInfo
import android.view.accessibility.AccessibilityEvent
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.ui.pause.PauseOverlayHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Detects app launches from accessibility window events.
 *
 * This replaces the `UsageStatsManager` poll loop in `AppMonitorService`. The window
 * event arrives as the app comes to the front. It does not guarantee that no app
 * content is briefly visible, and no polling timer runs while the screen is on.
 *
 * It also removes three permissions and a Play Console obligation: no foreground service
 * means no `FOREGROUND_SERVICE_SPECIAL_USE` declaration (and no demo video justifying
 * it), and the system rebinds an accessibility service after reboot on its own, so no
 * boot receiver is needed either.
 *
 * Detection is the only thing that changes. Approval, the overlay, per-app pause length
 * and everything the pause screen does are reused unchanged.
 */
@AndroidEntryPoint
class BreatheAccessibilityService : AccessibilityService() {

    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var sessionApprovalStore: SessionApprovalStore
    @Inject lateinit var pauseOverlayHost: PauseOverlayHost
    @Inject lateinit var monitoringStatus: MonitoringStatus
    @Inject lateinit var snoozeStore: SnoozeStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var blockedAppsJob: Job? = null
    private var snoozeJob: Job? = null

    /** Mirrors the blocked table so the event path never touches the database. */
    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    private val visits = ForegroundVisits()
    private var transientPackages: Set<String> = setOf("com.android.systemui")
    private var receiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) resetVisit()
        }
    }

    private fun resetVisit() {
        pauseOverlayHost.hide()
        sessionApprovalStore.clear()
        visits.reset()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        monitoringStatus.connected()
        transientPackages = setOf("com.android.systemui") +
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        if (snoozeJob?.isActive != true) {
            snoozeJob = scope.launch {
                snoozeStore.deadline.collect { until ->
                    if (until > 0) resetVisit()
                    else { sessionApprovalStore.clear(); visits.reset() }
                }
            }
        }
        if (blockedAppsJob?.isActive == true) { monitoringStatus.loaded(); return }
        blockedAppsJob = scope.launch {
            appRepository.getBlockedApps().collect { apps ->
                val next = apps.map { it.packageName }.toSet()
                (blockedPackages - next).forEach(sessionApprovalStore::revoke)
                if (pauseOverlayHost.activePackage?.let { it !in next } == true) pauseOverlayHost.hide()
                blockedPackages = next
                monitoringStatus.loaded()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val current = event.packageName?.toString() ?: return
        if (snoozeStore.isSnoozed()) { resetVisit(); return }
        // Our overlay emits our package name too; only the real MainActivity is a
        // departure. A keyboard or notification shade is not a new app visit.
        val transient = current in transientPackages ||
            (current == packageName && event.className?.toString() != MainActivity::class.java.name)
        val change = visits.observe(current, pauseOverlayHost.activePackage, transient) ?: return
        sessionApprovalStore.revoke(change.departedPackage)
        if (change.dismissPause) pauseOverlayHost.hide()
        if (!getSystemService(PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        if (pauseOverlayHost.isShowing) return

        if (current !in blockedPackages) return
        if (sessionApprovalStore.isApproved(current)) return
        if (!pauseOverlayHost.canShow()) return

        pauseOverlayHost.show(current, resolveAppName(current))
    }

    private fun resolveAppName(packageName: String): String = runCatching {
        val info = this.packageManager.getApplicationInfo(packageName, 0)
        this.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private fun disconnect() {
        resetVisit()
        blockedAppsJob?.cancel()
        blockedAppsJob = null
        snoozeJob?.cancel()
        snoozeJob = null
        monitoringStatus.disconnected()
        if (receiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            receiverRegistered = false
        }
    }

    companion object {
        /** Whether the user has enabled the service in Android's accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expected = ComponentName(context, BreatheAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}

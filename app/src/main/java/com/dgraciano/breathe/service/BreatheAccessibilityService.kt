package com.dgraciano.breathe.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
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
 * event arrives as the app comes to the front, so the pause appears before it draws
 * rather than up to half a poll interval later, and there is no timer running while the
 * screen is on.
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var blockedAppsJob: Job? = null

    /** Mirrors the blocked table so the event path never touches the database. */
    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    private var lastForeground: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (blockedAppsJob?.isActive == true) return
        blockedAppsJob = scope.launch {
            appRepository.getBlockedApps().collect { apps ->
                blockedPackages = apps.map { it.packageName }.toSet()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val current = event.packageName?.toString() ?: return
        if (current == packageName) return

        // The overlay draws over the blocked app without displacing it, so events for
        // that app keep arriving while the pause is up. Ignore them.
        if (pauseOverlayHost.isShowing) return

        if (current != lastForeground) {
            // Approval is per-visit: leaving the app ends it. Only the app being left is
            // revoked, and lastForeground is never reset to null, so an approval cannot
            // outlive the visit it was granted for.
            sessionApprovalStore.revoke(lastForeground)
            lastForeground = current
        }

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
        pauseOverlayHost.hide()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        pauseOverlayHost.hide()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Whether the user has enabled the service in Android's accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { entry ->
                entry.startsWith(context.packageName) &&
                    entry.contains(BreatheAccessibilityService::class.java.simpleName)
            }
        }
    }
}

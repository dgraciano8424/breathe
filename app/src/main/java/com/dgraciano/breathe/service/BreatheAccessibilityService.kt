package com.dgraciano.breathe.service

import android.accessibilityservice.AccessibilityService
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.MentalHealthTipsRepository
import com.dgraciano.breathe.data.repository.SettingsRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.ui.pause.DEFAULT_PAUSE_SECONDS
import com.dgraciano.breathe.ui.pause.PauseOverlay
import com.dgraciano.breathe.ui.pause.PauseScreen
import com.dgraciano.breathe.ui.theme.BreatheTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Detects app launches from accessibility window events instead of polling UsageStats.
 *
 * This replaces AppMonitorService's 500ms poll loop. Events arrive as the target app comes
 * to the front, so the pause appears before it draws rather than up to half a second after,
 * and there is no foreground service, no battery drain, and no boot receiver to keep alive
 * (the system rebinds an accessibility service itself).
 */
@AndroidEntryPoint
class BreatheAccessibilityService : AccessibilityService() {

    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var statsRepository: StatsRepository
    @Inject lateinit var tipsRepository: MentalHealthTipsRepository
    @Inject lateinit var sessionTimeHelper: SessionTimeHelper
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Cached so a blocked-app check never touches the database on the event path. */
    @Volatile private var blockedPackages: Set<String> = emptySet()

    /** Mirrors the user's chosen pause length; read on the event path, so kept in memory. */
    @Volatile private var pauseSeconds: Int = DEFAULT_PAUSE_SECONDS

    /** Approval is granted only on an explicit choice and expires on wall-clock time. */
    private val approvals = ApprovalRegistry()

    private var overlay: PauseOverlay? = null
    private var pausedPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            appRepository.getBlockedApps().collect { apps ->
                blockedPackages = apps.map { it.packageName }.toSet()
            }
        }
        scope.launch {
            settingsRepository.pauseSeconds.collect { pauseSeconds = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Leaving the paused app dismisses the overlay; the user got where they wanted.
        if (overlay?.isShowing == true && pkg != pausedPackage) {
            dismissOverlay()
            return
        }

        if (pkg !in blockedPackages) return
        if (approvals.isApproved(pkg)) return
        if (overlay?.isShowing == true) return
        if (!Settings.canDrawOverlays(this)) return

        showPause(pkg)
    }

    private fun showPause(pkg: String) {
        pausedPackage = pkg
        val appName = resolveAppName(pkg)

        scope.launch {
            val attempt = withContext(Dispatchers.IO) {
                runCatching { statsRepository.getTodayAttemptCount(pkg) + 1 }.getOrDefault(1)
            }
            val tip = tipsRepository.getRandomTip()
            val alternative = tipsRepository.getRandomActivity()

            val host = PauseOverlay(this@BreatheAccessibilityService)
            overlay = host

            host.show(onBackPressed = { decline(pkg, appName) }) {
                BreatheTheme {
                    // Must be remembered: a bare mutableStateOf is reallocated on every
                    // recomposition, so the selection would never stick.
                    var reason by remember { mutableStateOf<String?>(null) }
                    PauseScreen(
                        appName = appName,
                        attemptCount = attempt,
                        tip = tip,
                        alternativeActivity = alternative,
                        selectedReason = reason,
                        onReasonSelected = { picked ->
                            reason = if (reason == picked) null else picked
                        },
                        onYes = { allow(pkg, appName, reason) },
                        onNo = { decline(pkg, appName, reason) },
                        pauseSeconds = pauseSeconds
                    )
                }
            }
        }
    }

    private fun allow(pkg: String, appName: String, reason: String? = null) {
        approvals.approve(pkg)
        record(pkg, appName, InterventionEvent.OUTCOME_OPENED, reason, minutesSaved = 0)
        dismissOverlay()
    }

    private fun decline(pkg: String, appName: String, reason: String? = null) {
        approvals.revoke(pkg)
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { sessionTimeHelper.getAvgSessionMinutes(pkg) }.getOrDefault(0)
            }
            record(pkg, appName, InterventionEvent.OUTCOME_DECLINED, reason, saved)
        }
        dismissOverlay()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun record(
        pkg: String,
        appName: String,
        outcome: String,
        reason: String?,
        minutesSaved: Int
    ) {
        // Runs on the service scope, not a screen's scope, so dismissing the overlay
        // cannot cancel the write.
        scope.launch(Dispatchers.IO) {
            runCatching {
                statsRepository.recordEvent(
                    InterventionEvent(
                        packageName = pkg,
                        appName = appName,
                        outcome = outcome,
                        reason = reason,
                        minutesSaved = minutesSaved
                    )
                )
            }
        }
    }

    private fun dismissOverlay() {
        overlay?.dismiss()
        overlay = null
        pausedPackage = null
    }

    private fun resolveAppName(pkg: String): String = runCatching {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        dismissOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        dismissOverlay()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun isEnabled(context: android.content.Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any {
                it.startsWith(context.packageName) &&
                    it.contains(BreatheAccessibilityService::class.java.simpleName)
            }
        }
    }
}

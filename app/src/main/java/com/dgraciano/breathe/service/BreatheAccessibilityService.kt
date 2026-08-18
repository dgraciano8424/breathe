package com.dgraciano.breathe.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.dgraciano.breathe.data.repository.AppRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Thin Android adapter for the process-wide [InterventionCoordinator]. */
@AndroidEntryPoint
class BreatheAccessibilityService : AccessibilityService() {

    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var coordinator: InterventionCoordinator

    private val normalizer = ForegroundObservationNormalizer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var blockedAppsJob: Job? = null
    private var recoveryJob: Job? = null
    private var connected = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (connected) return
        connected = true
        publishPermissionTruth()
        coordinator.onServiceConnected()
        restartMonitoredPackages()
        val recoveryAtConnection = coordinator.recoveryRequest.value
        recoveryJob = scope.launch {
            // A recovery request is an event. Do not replay an already handled request when
            // Android reconnects this adapter to the same process-wide coordinator.
            coordinator.recoveryRequest.collect {
                if (it is MonitoringRecoveryRequest.ReinitializeMonitoring &&
                    it != recoveryAtConnection
                ) {
                    restartMonitoredPackages()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val observedPackage = event.packageName?.toString() ?: return

        publishPermissionTruth()
        try {
            val facts = foregroundFacts(observedPackage)
            coordinator.onForegroundObservation(
                ForegroundObservation(
                    packageName = observedPackage,
                    componentName = event.className?.toString(),
                    observedAtElapsedMs = SystemClock.elapsedRealtime(),
                    surface = normalizer.classify(facts),
                ),
            )
        } catch (cause: Exception) {
            coordinator.onClassifierFailure(cause)
        }
    }

    private fun foregroundFacts(observedPackage: String): ForegroundFacts = ForegroundFacts(
        packageName = observedPackage,
        ownPackage = packageName,
        activeImePackage = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        )?.substringBefore('/'),
        systemUiPackages = SYSTEM_UI_PACKAGES,
        settingsPackages = resolveSettingsPackages(),
        launchablePackages = if (packageManager.getLaunchIntentForPackage(observedPackage) != null) {
            setOf(observedPackage)
        } else {
            emptySet()
        },
    )

    private fun resolveSettingsPackages(): Set<String> = buildSet {
        add(ANDROID_SETTINGS_PACKAGE)
        listOf(
            Settings.ACTION_ACCESSIBILITY_SETTINGS,
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        ).forEach { action ->
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(Intent(action), 0)?.activityInfo?.packageName?.let(::add)
        }
    }

    private fun restartMonitoredPackages() {
        blockedAppsJob?.cancel()
        blockedAppsJob = scope.launch {
            appRepository.getBlockedApps()
                .catch { cause -> coordinator.onMonitoredPackagesUnavailable(cause) }
                .collect { apps ->
                    coordinator.onMonitoredPackagesReady(apps.mapTo(hashSetOf()) { it.packageName })
                }
        }
    }

    private fun publishPermissionTruth() {
        coordinator.updatePermissions(
            accessibilityGranted = AccessibilityServiceState.isEnabled(this),
            overlayGranted = Settings.canDrawOverlays(this),
        )
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnectCoordinator()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnectCoordinator()
        scope.cancel()
        super.onDestroy()
    }

    private fun disconnectCoordinator() {
        if (!connected) return
        connected = false
        blockedAppsJob?.cancel()
        blockedAppsJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        coordinator.onServiceDisconnected()
    }

    companion object {
        private const val ANDROID_SETTINGS_PACKAGE = "com.android.settings"
        private val SYSTEM_UI_PACKAGES = setOf("com.android.systemui")

        /** Kept as the public query used by existing non-onboarding consumers. */
        fun isEnabled(context: Context): Boolean = AccessibilityServiceState.isEnabled(context)
    }
}

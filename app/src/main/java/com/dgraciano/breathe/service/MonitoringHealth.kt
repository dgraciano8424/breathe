package com.dgraciano.breathe.service

/** The complete monitoring status exposed to UI and Android adapters. */
sealed interface MonitoringHealth {
    data class SetupRequired(val reason: SetupRequiredReason) : MonitoringHealth

    data object Initializing : MonitoringHealth

    data class Active(val lastEventAtElapsedMs: Long?) : MonitoringHealth

    data class Degraded(
        val reason: DegradedReason,
        val recoveryAction: MonitoringRecoveryAction? = null,
    ) : MonitoringHealth
}

enum class SetupRequiredReason {
    Accessibility,
    Overlay,
}

/** Closed reasons keep health copy deterministic and prevent technical errors reaching the UI. */
enum class DegradedReason {
    PresentationNotConfirmed,
    OverlayUnavailable,
    ServiceDisconnected,
    PermissionChanged,
    MonitoredAppsUnavailable,
    ClassifierFailure,
}

enum class MonitoringRecoveryAction {
    TryAgain,
    ReviewSetup,
    ReviewAccessibilitySettings,
    ReviewDisplayPermission,
}

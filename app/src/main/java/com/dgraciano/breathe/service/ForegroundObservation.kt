package com.dgraciano.breathe.service

/** The package facts resolved by the Android adapter before foreground classification. */
data class ForegroundFacts(
    val packageName: String,
    val ownPackage: String,
    val activeImePackage: String?,
    val systemUiPackages: Set<String>,
    val settingsPackages: Set<String>,
    val launchablePackages: Set<String>,
)

/**
 * A foreground surface is either an app visit worth considering or an interruption that
 * must not change the current visit.
 */
enum class ForegroundSurface {
    ELIGIBLE_APP,
    BREATHE,
    SYSTEM_UI,
    ACTIVE_IME,
    SETTINGS_OR_PERMISSION,
    NON_LAUNCHABLE,
}

data class ForegroundObservation(
    val packageName: String,
    val componentName: String?,
    val observedAtElapsedMs: Long,
    val surface: ForegroundSurface,
)

/**
 * The classifier keeps visit state in memory deliberately. A service process restart starts from
 * this empty state, so a previously approved app may be prompted once after recovery.
 */
data class ForegroundClassifierState(
    /** Null until the initial monitored-package snapshot has arrived. */
    val monitoredPackages: Set<String>? = null,
    /** The most recent eligible observation received while [monitoredPackages] was unavailable. */
    val deferredEligibleObservation: ForegroundObservation? = null,
    /** The app for the current meaningful foreground visit. */
    val currentEligiblePackage: String? = null,
    /** Visit approvals that have not yet been ended by a meaningful app transition. */
    val approvedPackages: Set<String> = emptySet(),
    /** Prevents repeated window events from requesting another pause for the same visit. */
    val pauseRequestedForCurrentVisit: Boolean = false,
) {
    val monitoredPackagesReady: Boolean
        get() = monitoredPackages != null
}

sealed interface ForegroundAction {
    data class RevokeApproval(val packageName: String) : ForegroundAction
    data class RequestPause(val packageName: String) : ForegroundAction
}

enum class ForegroundIgnoreReason {
    BREATHE,
    SYSTEM_SURFACE,
    SETTINGS_OR_PERMISSION,
    NON_LAUNCHABLE,
}

data class ForegroundTransition(
    val state: ForegroundClassifierState,
    val actions: List<ForegroundAction>,
    /** Present when an observation was intentionally ignored, without creating executable work. */
    val ignoredReason: ForegroundIgnoreReason? = null,
)

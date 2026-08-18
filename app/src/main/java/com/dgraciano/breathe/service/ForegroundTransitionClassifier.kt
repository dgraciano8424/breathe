package com.dgraciano.breathe.service

/**
 * Pure reducer for meaningful app visits. Non-app surfaces intentionally leave state untouched:
 * notification shade, keyboards, and permission dialogs must not end an approved app visit.
 */
class ForegroundTransitionClassifier {

    fun reduce(
        state: ForegroundClassifierState,
        observation: ForegroundObservation,
    ): ForegroundTransition {
        if (observation.surface != ForegroundSurface.ELIGIBLE_APP) {
            return ForegroundTransition(
                state = state,
                actions = emptyList(),
                ignoredReason = observation.surface.ignoreReason(),
            )
        }

        if (!state.monitoredPackagesReady) {
            return ForegroundTransition(
                state = state.copy(deferredEligibleObservation = observation),
                actions = emptyList(),
            )
        }

        return reduceEligible(state, observation)
    }

    /**
     * Marks the initial monitored-package snapshot ready and processes the latest eligible event
     * exactly once. Repeated readiness notifications only refresh the package set.
     */
    fun reconcileAfterMonitoredPackagesReady(
        state: ForegroundClassifierState,
        packages: Set<String>,
    ): ForegroundTransition {
        if (state.monitoredPackagesReady) {
            return ForegroundTransition(state.copy(monitoredPackages = packages), emptyList())
        }

        val ready = state.copy(
            monitoredPackages = packages,
            deferredEligibleObservation = null,
        )
        val deferred = state.deferredEligibleObservation
            ?: return ForegroundTransition(ready, emptyList())

        return reduceEligible(ready, deferred)
    }

    /** Records a user approval for the active visit; approvals never outlive a meaningful leave. */
    fun approve(state: ForegroundClassifierState, packageName: String): ForegroundClassifierState =
        if (state.currentEligiblePackage == packageName) {
            state.copy(approvedPackages = state.approvedPackages + packageName)
        } else {
            state
        }

    fun approveCurrentVisit(state: ForegroundClassifierState): ForegroundClassifierState =
        state.currentEligiblePackage?.let { approve(state, it) } ?: state

    private fun reduceEligible(
        state: ForegroundClassifierState,
        observation: ForegroundObservation,
    ): ForegroundTransition {
        if (state.currentEligiblePackage == observation.packageName) {
            return ForegroundTransition(state, emptyList())
        }

        val previousPackage = state.currentEligiblePackage
        val actions = mutableListOf<ForegroundAction>()
        val remainingApprovals = if (previousPackage in state.approvedPackages) {
            actions += ForegroundAction.RevokeApproval(previousPackage!!)
            state.approvedPackages - previousPackage
        } else {
            state.approvedPackages
        }

        var nextState = state.copy(
            currentEligiblePackage = observation.packageName,
            approvedPackages = remainingApprovals,
            pauseRequestedForCurrentVisit = false,
        )
        if (observation.packageName in nextState.monitoredPackages.orEmpty() &&
            observation.packageName !in nextState.approvedPackages
        ) {
            actions += ForegroundAction.RequestPause(observation.packageName)
            nextState = nextState.copy(pauseRequestedForCurrentVisit = true)
        }

        return ForegroundTransition(nextState, actions)
    }

    private fun ForegroundSurface.ignoreReason(): ForegroundIgnoreReason = when (this) {
        ForegroundSurface.BREATHE -> ForegroundIgnoreReason.BREATHE
        ForegroundSurface.SYSTEM_UI,
        ForegroundSurface.ACTIVE_IME,
        -> ForegroundIgnoreReason.SYSTEM_SURFACE
        ForegroundSurface.SETTINGS_OR_PERMISSION -> ForegroundIgnoreReason.SETTINGS_OR_PERMISSION
        ForegroundSurface.NON_LAUNCHABLE -> ForegroundIgnoreReason.NON_LAUNCHABLE
        ForegroundSurface.ELIGIBLE_APP -> error("Eligible apps are not ignored")
    }
}

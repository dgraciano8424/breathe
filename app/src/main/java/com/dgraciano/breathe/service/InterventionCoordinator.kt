package com.dgraciano.breathe.service

import com.dgraciano.breathe.data.repository.DecisionOutcome
import com.dgraciano.breathe.data.repository.PauseDecision
import com.dgraciano.breathe.data.repository.PauseDecisionSink
import com.dgraciano.breathe.di.ApplicationScope
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide boundary between foreground classification, pause presentation, and persistence.
 * Android components report facts and callbacks here; they do not own visit or retry state.
 */
@Singleton
class InterventionCoordinator @Inject constructor(
    private val presentation: PausePresentation,
    private val decisionSink: PauseDecisionSink,
    private val approvals: SessionApprovalStore,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : PausePresentationCallbacks {

    private val classifier = ForegroundTransitionClassifier()
    private var classifierState = ForegroundClassifierState()
    private var accessibilityGranted: Boolean? = null
    private var overlayGranted: Boolean? = null
    private var serviceConnected = false
    private var hasBeenActive = false
    private var initializationRetryUsed = false
    private var lastEventAtElapsedMs: Long? = null
    private var pending: PendingPresentation? = null
    private var retryCandidate: RetryCandidate? = null

    private val mutableHealth = MutableStateFlow<MonitoringHealth>(MonitoringHealth.Initializing)
    val health: StateFlow<MonitoringHealth> = mutableHealth.asStateFlow()

    @Synchronized
    fun updatePermissions(accessibilityGranted: Boolean, overlayGranted: Boolean) {
        val permissionWasRevoked = (hasBeenActive || pending != null) &&
            (this.accessibilityGranted == true && !accessibilityGranted) ||
            (hasBeenActive || pending != null) &&
                (this.overlayGranted == true && !overlayGranted)
        this.accessibilityGranted = accessibilityGranted
        this.overlayGranted = overlayGranted

        if (permissionWasRevoked) {
            closePending(keepForRetry = false)
            mutableHealth.value = MonitoringHealth.Degraded(
                reason = DegradedReason.PermissionChanged,
                recoveryAction = MonitoringRecoveryAction.ReviewSetup,
            )
        } else {
            publishReadiness()
        }
    }

    @Synchronized
    fun onServiceConnected() {
        serviceConnected = true
        publishReadiness()
    }

    @Synchronized
    fun onServiceDisconnected() {
        serviceConnected = false
        closePending(keepForRetry = false)
        mutableHealth.value = MonitoringHealth.Degraded(
            reason = DegradedReason.ServiceDisconnected,
            recoveryAction = MonitoringRecoveryAction.ReviewAccessibilitySettings,
        )
    }

    @Synchronized
    fun onMonitoredPackagesReady(packages: Set<String>) {
        runClassifier {
            classifier.reconcileAfterMonitoredPackagesReady(classifierState, packages)
        }?.let(::applyTransition)
        if (classifierState.monitoredPackagesReady) initializationRetryUsed = false
        if (pending == null && mutableHealth.value !is MonitoringHealth.Degraded) {
            publishReadiness()
        } else if (mutableHealth.value is MonitoringHealth.Degraded &&
            (mutableHealth.value as MonitoringHealth.Degraded).reason ==
            DegradedReason.MonitoredAppsUnavailable
        ) {
            publishReadiness()
        }
    }

    @Synchronized
    fun onMonitoredPackagesUnavailable(cause: Throwable? = null) {
        @Suppress("UNUSED_VARIABLE")
        val diagnosticCause = cause
        classifierState = ForegroundClassifierState()
        closePending(keepForRetry = false)
        mutableHealth.value = MonitoringHealth.Degraded(
            reason = DegradedReason.MonitoredAppsUnavailable,
            recoveryAction = initializationRecoveryAction(),
        )
    }

    @Synchronized
    fun onForegroundObservation(observation: ForegroundObservation) {
        if (observation.surface == ForegroundSurface.ELIGIBLE_APP) {
            lastEventAtElapsedMs = observation.observedAtElapsedMs
            if (mutableHealth.value is MonitoringHealth.Active) publishReadiness()
        }
        runClassifier { classifier.reduce(classifierState, observation) }?.let(::applyTransition)
    }

    /** Executes the one coordinator-owned recovery attempt advertised by health state. */
    @Synchronized
    fun retry() {
        val degraded = mutableHealth.value as? MonitoringHealth.Degraded ?: return
        if (degraded.recoveryAction != MonitoringRecoveryAction.TryAgain) return

        val candidate = retryCandidate
        if (candidate != null) {
            showPause(
                packageName = candidate.packageName,
                observedAtElapsedMs = candidate.observedAtElapsedMs,
                retryCount = candidate.retryCount + 1,
            )
        } else {
            initializationRetryUsed = true
            classifierState = ForegroundClassifierState()
            mutableHealth.value = MonitoringHealth.Initializing
        }
    }

    @Synchronized
    fun acknowledgeActivityContentReady(requestId: String) {
        acknowledgePresentation(requestId)
    }

    @Synchronized
    override fun onOverlayAdded(requestId: String) {
        acknowledgePresentation(requestId)
    }

    @Synchronized
    override fun onOverlayFailed(requestId: String, cause: Throwable) {
        // The presentation adapter owns its Activity fallback. Keep the same request open and
        // guarded by the acknowledgement timeout while Android attempts that fallback.
        if (pending?.request?.id != requestId) return
    }

    @Synchronized
    override fun onActivityContentReady(requestId: String) {
        acknowledgePresentation(requestId)
    }

    @Synchronized
    override fun onPresentationLost(requestId: String, cause: Throwable?) {
        val current = pending ?: return
        if (current.request.id != requestId) return
        degradePresentation(
            current = current,
            reason = DegradedReason.OverlayUnavailable,
        )
    }

    @Synchronized
    fun dismissWithBack(requestId: String) {
        val current = pending ?: return
        if (current.request.id != requestId) return
        approveCurrentVisit(current.request.packageName)
        closePending(keepForRetry = false)
        publishReadiness()
    }

    fun recordOpened(requestId: String, appName: String, reason: String?) {
        finishWithDecision(requestId, appName, reason, DecisionOutcome.OPENED)
    }

    fun recordDeclined(requestId: String, appName: String, reason: String?) {
        finishWithDecision(requestId, appName, reason, DecisionOutcome.DECLINED)
    }

    @Synchronized
    private fun finishWithDecision(
        requestId: String,
        appName: String,
        reason: String?,
        outcome: DecisionOutcome,
    ) {
        val current = pending ?: return
        if (current.request.id != requestId) return
        if (outcome == DecisionOutcome.OPENED) {
            approveCurrentVisit(current.request.packageName)
        }
        val decision = PauseDecision(
            requestId = requestId,
            packageName = current.request.packageName,
            appName = appName,
            outcome = outcome,
            reason = reason,
        )
        closePending(keepForRetry = false)
        publishReadiness()
        appScope.launch { decisionSink.record(decision) }
    }

    private fun applyTransition(transition: ForegroundTransition) {
        classifierState = transition.state
        transition.actions.forEach { action ->
            when (action) {
                is ForegroundAction.RevokeApproval -> approvals.revoke(action.packageName)
                is ForegroundAction.RequestPause -> showPause(
                    packageName = action.packageName,
                    observedAtElapsedMs = lastEventAtElapsedMs ?: 0L,
                    retryCount = 0,
                )
            }
        }
    }

    private fun showPause(
        packageName: String,
        observedAtElapsedMs: Long,
        retryCount: Int,
    ) {
        closePending(keepForRetry = false)
        val request = PauseRequest(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            observedAtElapsedMs = observedAtElapsedMs,
        )
        val timeoutJob = appScope.launch {
            delay(PRESENTATION_TIMEOUT_MS)
            onPresentationTimeout(request.id)
        }
        pending = PendingPresentation(request, timeoutJob, retryCount)
        retryCandidate = null
        publishReadiness()
        try {
            presentation.show(request, this)
        } catch (_: Exception) {
            pending?.takeIf { it.request.id == request.id }?.let {
                degradePresentation(it, DegradedReason.OverlayUnavailable)
            }
        }
    }

    @Synchronized
    private fun onPresentationTimeout(requestId: String) {
        val current = pending ?: return
        if (current.request.id != requestId || current.confirmed) return
        degradePresentation(current, DegradedReason.PresentationNotConfirmed)
    }

    private fun degradePresentation(current: PendingPresentation, reason: DegradedReason) {
        retryCandidate = RetryCandidate(
            packageName = current.request.packageName,
            observedAtElapsedMs = current.request.observedAtElapsedMs,
            retryCount = current.retryCount,
        )
        closePending(keepForRetry = true)
        mutableHealth.value = MonitoringHealth.Degraded(
            reason = reason,
            recoveryAction = if (current.retryCount == 0) {
                MonitoringRecoveryAction.TryAgain
            } else {
                MonitoringRecoveryAction.ReviewSetup
            },
        )
    }

    private fun acknowledgePresentation(requestId: String) {
        val current = pending ?: return
        if (current.request.id != requestId || current.confirmed) return
        current.confirmed = true
        current.timeoutJob.cancel()
        retryCandidate = null
        publishReadiness()
    }

    private fun approveCurrentVisit(packageName: String) {
        classifierState = classifier.approve(classifierState, packageName)
        approvals.approve(packageName)
    }

    private fun closePending(keepForRetry: Boolean) {
        val current = pending ?: run {
            if (!keepForRetry) retryCandidate = null
            return
        }
        pending = null
        current.timeoutJob.cancel()
        presentation.dismiss(current.request.id)
        if (!keepForRetry) retryCandidate = null
    }

    private fun publishReadiness() {
        val next = when {
            accessibilityGranted == false ->
                MonitoringHealth.SetupRequired(SetupRequiredReason.Accessibility)
            overlayGranted == false -> MonitoringHealth.SetupRequired(SetupRequiredReason.Overlay)
            accessibilityGranted != true || overlayGranted != true -> MonitoringHealth.Initializing
            !serviceConnected || !classifierState.monitoredPackagesReady -> MonitoringHealth.Initializing
            else -> MonitoringHealth.Active(lastEventAtElapsedMs)
        }
        mutableHealth.value = next
        if (next is MonitoringHealth.Active) hasBeenActive = true
    }

    private inline fun runClassifier(block: () -> ForegroundTransition): ForegroundTransition? =
        try {
            block()
        } catch (_: Exception) {
            closePending(keepForRetry = false)
            mutableHealth.value = MonitoringHealth.Degraded(
                reason = DegradedReason.ClassifierFailure,
                recoveryAction = initializationRecoveryAction(),
            )
            null
        }

    private fun initializationRecoveryAction(): MonitoringRecoveryAction =
        if (initializationRetryUsed) {
            MonitoringRecoveryAction.ReviewSetup
        } else {
            MonitoringRecoveryAction.TryAgain
        }

    private data class PendingPresentation(
        val request: PauseRequest,
        val timeoutJob: Job,
        val retryCount: Int,
        var confirmed: Boolean = false,
    )

    private data class RetryCandidate(
        val packageName: String,
        val observedAtElapsedMs: Long,
        val retryCount: Int,
    )

    private companion object {
        const val PRESENTATION_TIMEOUT_MS = 2_500L
    }
}

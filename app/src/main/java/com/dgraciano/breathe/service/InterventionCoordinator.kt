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
    private var initializationRetryUsed = false
    private var recoveryInProgress = false
    private var recoverySequence = 0L
    private var lastEventAtElapsedMs: Long? = null
    private var pending: PendingPresentation? = null
    private var deferredPause: DeferredPause? = null
    private val activeFailures = mutableMapOf<DegradedReason, MonitoringRecoveryAction>()

    private val mutableHealth = MutableStateFlow<MonitoringHealth>(MonitoringHealth.Initializing)
    val health: StateFlow<MonitoringHealth> = mutableHealth.asStateFlow()
    private val mutableDiagnostic = MutableStateFlow<MonitoringDiagnostic?>(null)
    val diagnostic: StateFlow<MonitoringDiagnostic?> = mutableDiagnostic.asStateFlow()
    private val mutableRecoveryRequest =
        MutableStateFlow<MonitoringRecoveryRequest?>(null)
    val recoveryRequest: StateFlow<MonitoringRecoveryRequest?> =
        mutableRecoveryRequest.asStateFlow()

    @Synchronized
    fun updatePermissions(accessibilityGranted: Boolean, overlayGranted: Boolean) {
        val permissionWasRevoked =
            (this.accessibilityGranted == true && !accessibilityGranted) ||
                (this.overlayGranted == true && !overlayGranted)
        val presentationWasActive = pending != null
        this.accessibilityGranted = accessibilityGranted
        this.overlayGranted = overlayGranted

        if (permissionWasRevoked && presentationWasActive) {
            closePending(keepForRetry = false)
            activateFailure(
                reason = DegradedReason.PermissionChanged,
                recoveryAction = MonitoringRecoveryAction.ReviewSetup,
            )
        } else {
            publishReadiness(clearing = setOf(DegradedReason.PermissionChanged))
            presentDeferredIfReady()
        }
    }

    @Synchronized
    fun onServiceConnected() {
        serviceConnected = true
        publishReadiness(clearing = setOf(DegradedReason.ServiceDisconnected))
        presentDeferredIfReady()
    }

    @Synchronized
    fun onServiceDisconnected() {
        serviceConnected = false
        closePending(keepForRetry = false)
        activateFailure(
            reason = DegradedReason.ServiceDisconnected,
            recoveryAction = MonitoringRecoveryAction.ReviewAccessibilitySettings,
        )
    }

    @Synchronized
    fun onMonitoredPackagesReady(packages: Set<String>) {
        val hadMonitoredPackageFailure =
            DegradedReason.MonitoredAppsUnavailable in activeFailures
        val deferredObservedAt =
            classifierState.firstDeferredEligibleObservation?.observedAtElapsedMs
        runClassifier {
            classifier.reconcileAfterMonitoredPackagesReady(classifierState, packages.toHashSet())
        }?.let { applyTransition(it, deferredObservedAt) }
        val recoveryCompleted = classifierState.monitoredPackagesReady && recoveryInProgress
        publishReadiness(
            clearing = buildSet {
                add(DegradedReason.MonitoredAppsUnavailable)
                if (recoveryInProgress) {
                    add(DegradedReason.ClassifierFailure)
                    add(DegradedReason.PresentationNotConfirmed)
                }
            },
        )
        if (classifierState.monitoredPackagesReady &&
            (recoveryCompleted || hadMonitoredPackageFailure)
        ) {
            initializationRetryUsed = false
            recoveryInProgress = false
        }
        presentDeferredIfReady()
    }

    @Synchronized
    fun onMonitoredPackagesUnavailable(cause: Throwable? = null) {
        recordDiagnostic(DegradedReason.MonitoredAppsUnavailable, cause)
        classifierState = ForegroundClassifierState()
        closePending(keepForRetry = false)
        activateFailure(
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
        runClassifier { classifier.reduce(classifierState, observation) }
            ?.let { applyTransition(it, observation.observedAtElapsedMs) }
    }

    @Synchronized
    fun onClassifierFailure(cause: Throwable) {
        recordDiagnostic(DegradedReason.ClassifierFailure, cause)
        closePending(keepForRetry = false)
        deferredPause = null
        activateFailure(
            reason = DegradedReason.ClassifierFailure,
            recoveryAction = initializationRecoveryAction(),
        )
    }

    /** Executes the one coordinator-owned recovery attempt advertised by health state. */
    @Synchronized
    fun retry() {
        val degraded = mutableHealth.value as? MonitoringHealth.Degraded ?: return
        if (degraded.recoveryAction != MonitoringRecoveryAction.TryAgain) return

        initializationRetryUsed = true
        recoveryInProgress = true
        activeFailures.remove(degraded.reason)
        classifierState = ForegroundClassifierState()
        approvals.clear()
        deferredPause = null
        lastEventAtElapsedMs = null
        publishReadiness()
        recoverySequence += 1
        mutableRecoveryRequest.value =
            MonitoringRecoveryRequest.ReinitializeMonitoring(recoverySequence)
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
        recordDiagnostic(DegradedReason.OverlayUnavailable, cause)
    }

    @Synchronized
    override fun onActivityContentReady(requestId: String) {
        acknowledgePresentation(requestId)
    }

    @Synchronized
    override fun onPresentationLost(requestId: String, cause: Throwable?) {
        val current = pending ?: return
        if (current.request.id != requestId) return
        recordDiagnostic(DegradedReason.OverlayUnavailable, cause)
        degradePresentation(
            current = current,
            reason = DegradedReason.OverlayUnavailable,
        )
    }

    @Synchronized
    fun dismissWithBack(requestId: String) {
        val current = pending ?: return
        if (current.request.id != requestId || current.terminalClaimed) return
        approveCurrentVisit(current.request.packageName)
        closePending(keepForRetry = false)
        publishReadiness()
    }

    fun recordOpened(requestId: String, appName: String, reason: String?): Job? =
        finishWithDecision(requestId, appName, reason, DecisionOutcome.OPENED)

    fun recordDeclined(requestId: String, appName: String, reason: String?): Job? =
        finishWithDecision(requestId, appName, reason, DecisionOutcome.DECLINED)

    @Synchronized
    private fun finishWithDecision(
        requestId: String,
        appName: String,
        reason: String?,
        outcome: DecisionOutcome,
    ): Job? {
        val current = pending ?: return null
        if (current.request.id != requestId || current.terminalClaimed) return null
        current.terminalClaimed = true
        current.timeoutJob.cancel()
        val decision = PauseDecision(
            requestId = requestId,
            packageName = current.request.packageName,
            appName = appName,
            outcome = outcome,
            reason = reason,
        )
        return appScope.launch {
            val result = decisionSink.record(decision)
            if (result.isSuccess) {
                completeDecisionSuccess(requestId, outcome)
            } else {
                completeDecisionFailure(requestId, result.exceptionOrNull())
            }
        }
    }

    @Synchronized
    private fun completeDecisionSuccess(requestId: String, outcome: DecisionOutcome) {
        val current = pending ?: return
        if (current.request.id != requestId || !current.terminalClaimed) return
        if (outcome == DecisionOutcome.OPENED) approveCurrentVisit(current.request.packageName)
        closePending(keepForRetry = false)
        publishReadiness()
    }

    @Synchronized
    private fun completeDecisionFailure(requestId: String, cause: Throwable?) {
        val current = pending ?: return
        if (current.request.id != requestId || !current.terminalClaimed) return
        mutableDiagnostic.value = MonitoringDiagnostic(DegradedReason.ClassifierFailure, cause)
        activateFailure(
            reason = DegradedReason.ClassifierFailure,
            recoveryAction = MonitoringRecoveryAction.ReviewSetup,
        )
    }

    private fun applyTransition(
        transition: ForegroundTransition,
        requestObservedAtElapsedMs: Long?,
    ) {
        classifierState = transition.state
        if (deferredPause?.packageName != classifierState.currentEligiblePackage) {
            deferredPause = null
        }
        transition.actions.forEach { action ->
            when (action) {
                is ForegroundAction.RevokeApproval -> approvals.revoke(action.packageName)
                is ForegroundAction.RequestPause -> {
                    val deferred = DeferredPause(
                        packageName = action.packageName,
                        observedAtElapsedMs = requestObservedAtElapsedMs ?: 0L,
                    )
                    if (isReadyForPresentation()) {
                        showPause(
                            packageName = deferred.packageName,
                            observedAtElapsedMs = deferred.observedAtElapsedMs,
                            retryCount = 0,
                        )
                    } else {
                        deferredPause = deferred
                    }
                }
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
        publishReadiness()
        try {
            presentation.show(request, this)
        } catch (cause: Exception) {
            recordDiagnostic(DegradedReason.OverlayUnavailable, cause)
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
        closePending(keepForRetry = false)
        activateFailure(
            reason = reason,
            recoveryAction = when (reason) {
                DegradedReason.OverlayUnavailable ->
                    MonitoringRecoveryAction.ReviewDisplayPermission
                else -> initializationRecoveryAction()
            },
        )
    }

    private fun acknowledgePresentation(requestId: String) {
        val current = pending ?: return
        if (current.request.id != requestId || current.confirmed) return
        current.confirmed = true
        current.timeoutJob.cancel()
        publishReadiness(
            clearing = setOf(
                DegradedReason.PresentationNotConfirmed,
                DegradedReason.OverlayUnavailable,
            ),
        )
    }

    private fun approveCurrentVisit(packageName: String) {
        classifierState = classifier.approve(classifierState, packageName)
        approvals.approve(packageName)
    }

    private fun closePending(keepForRetry: Boolean) {
        val current = pending ?: run {
            return
        }
        pending = null
        current.timeoutJob.cancel()
        presentation.dismiss(current.request.id)
    }

    private fun publishReadiness(clearing: Set<DegradedReason> = emptySet()) {
        clearing.forEach(activeFailures::remove)
        publishHealth()
    }

    private fun publishHealth() {
        val activeFailure = FAILURE_PRIORITY.firstNotNullOfOrNull { reason ->
            activeFailures[reason]?.let { recoveryAction ->
                MonitoringHealth.Degraded(reason, recoveryAction)
            }
        }
        val next = activeFailure ?: when {
            accessibilityGranted == false ->
                MonitoringHealth.SetupRequired(SetupRequiredReason.Accessibility)
            overlayGranted == false -> MonitoringHealth.SetupRequired(SetupRequiredReason.Overlay)
            accessibilityGranted != true || overlayGranted != true -> MonitoringHealth.Initializing
            !serviceConnected || !classifierState.monitoredPackagesReady -> MonitoringHealth.Initializing
            else -> MonitoringHealth.Active(lastEventAtElapsedMs)
        }
        mutableHealth.value = next
    }

    private fun presentDeferredIfReady() {
        val deferred = deferredPause ?: return
        if (!isReadyForPresentation() ||
            classifierState.currentEligiblePackage != deferred.packageName
        ) return
        deferredPause = null
        showPause(deferred.packageName, deferred.observedAtElapsedMs, retryCount = 0)
    }

    private fun isReadyForPresentation(): Boolean =
        accessibilityGranted == true &&
            overlayGranted == true &&
            serviceConnected &&
            classifierState.monitoredPackagesReady

    private fun activateFailure(
        reason: DegradedReason,
        recoveryAction: MonitoringRecoveryAction,
    ) {
        activeFailures[reason] = recoveryAction
        publishHealth()
    }

    private inline fun runClassifier(block: () -> ForegroundTransition): ForegroundTransition? =
        try {
            block()
        } catch (cause: Exception) {
            recordDiagnostic(DegradedReason.ClassifierFailure, cause)
            closePending(keepForRetry = false)
            activateFailure(
                reason = DegradedReason.ClassifierFailure,
                recoveryAction = initializationRecoveryAction(),
            )
            null
        }

    private fun recordDiagnostic(reason: DegradedReason, cause: Throwable?) {
        mutableDiagnostic.value = MonitoringDiagnostic(reason, cause)
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
        var terminalClaimed: Boolean = false,
    )

    private data class DeferredPause(
        val packageName: String,
        val observedAtElapsedMs: Long,
    )

    private companion object {
        const val PRESENTATION_TIMEOUT_MS = 2_500L
        val FAILURE_PRIORITY = listOf(
            DegradedReason.ClassifierFailure,
            DegradedReason.PermissionChanged,
            DegradedReason.OverlayUnavailable,
            DegradedReason.PresentationNotConfirmed,
            DegradedReason.MonitoredAppsUnavailable,
            DegradedReason.ServiceDisconnected,
        )
    }
}

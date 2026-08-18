package com.dgraciano.breathe.service

import com.dgraciano.breathe.data.repository.DecisionOutcome
import com.dgraciano.breathe.data.repository.PauseDecision
import com.dgraciano.breathe.data.repository.PauseDecisionSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InterventionCoordinatorTest {

    @Test
    fun `overlay acknowledgement confirms presentation before timeout`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()

        fixture.coordinator.onForegroundObservation(selectedObservation())
        fixture.presentation.overlayAdded()
        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(MonitoringHealth.Active(1L), fixture.coordinator.health.value)
        assertEquals(1, fixture.presentation.showCount)
    }

    @Test
    fun `activity fallback acknowledgement confirms the same request`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val requestId = fixture.presentation.lastRequest.id

        fixture.presentation.overlayFailed()
        fixture.coordinator.acknowledgeActivityContentReady(requestId)
        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(MonitoringHealth.Active(1L), fixture.coordinator.health.value)
        assertEquals(1, fixture.presentation.showCount)
    }

    @Test
    fun `missing acknowledgement degrades and late acknowledgement is ignored`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val requestId = fixture.presentation.lastRequest.id

        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(
            DegradedReason.PresentationNotConfirmed,
            (fixture.coordinator.health.value as MonitoringHealth.Degraded).reason,
        )
        assertEquals(listOf(requestId), fixture.presentation.dismissedRequestIds)

        fixture.coordinator.acknowledgeActivityContentReady(requestId)

        assertTrue(fixture.coordinator.health.value is MonitoringHealth.Degraded)
    }

    @Test
    fun `presentation loss closes request and routes to display permission without retry`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val firstId = fixture.presentation.lastRequest.id

        fixture.presentation.presentationLost()

        assertEquals(
            MonitoringHealth.Degraded(
                reason = DegradedReason.OverlayUnavailable,
                recoveryAction = MonitoringRecoveryAction.ReviewDisplayPermission,
            ),
            fixture.coordinator.health.value,
        )
        assertEquals(listOf(firstId), fixture.presentation.dismissedRequestIds)

        fixture.coordinator.retry()
        assertEquals(1, fixture.presentation.showCount)
    }

    @Test
    fun `Back approves visit and records no decision`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val requestId = fixture.presentation.lastRequest.id

        fixture.coordinator.dismissWithBack(requestId)
        fixture.coordinator.onForegroundObservation(selectedObservation(at = 2L))
        runCurrent()

        assertEquals(1, fixture.presentation.showCount)
        assertTrue(fixture.approvals.isApproved(SELECTED_PACKAGE))
        assertTrue(fixture.decisions.recorded.isEmpty())
    }

    @Test
    fun `permission truth and all readiness prerequisites determine Active`() = runTest {
        val fixture = fixture(backgroundScope)

        assertEquals(MonitoringHealth.Initializing, fixture.coordinator.health.value)
        fixture.coordinator.updatePermissions(accessibilityGranted = false, overlayGranted = true)
        assertEquals(
            MonitoringHealth.SetupRequired(SetupRequiredReason.Accessibility),
            fixture.coordinator.health.value,
        )
        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = false)
        assertEquals(
            MonitoringHealth.SetupRequired(SetupRequiredReason.Overlay),
            fixture.coordinator.health.value,
        )
        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = true)
        assertEquals(MonitoringHealth.Initializing, fixture.coordinator.health.value)
        fixture.coordinator.onServiceConnected()
        assertEquals(MonitoringHealth.Initializing, fixture.coordinator.health.value)

        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE))

        assertEquals(MonitoringHealth.Active(lastEventAtElapsedMs = null), fixture.coordinator.health.value)
    }

    @Test
    fun `permission revoked during presentation degrades and closes request`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val requestId = fixture.presentation.lastRequest.id

        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = false)

        assertEquals(
            MonitoringHealth.Degraded(
                DegradedReason.PermissionChanged,
                MonitoringRecoveryAction.ReviewSetup,
            ),
            fixture.coordinator.health.value,
        )
        assertEquals(listOf(requestId), fixture.presentation.dismissedRequestIds)
    }

    @Test
    fun `idle permission revocation after activation requires setup`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()

        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = false)

        assertEquals(
            MonitoringHealth.SetupRequired(SetupRequiredReason.Overlay),
            fixture.coordinator.health.value,
        )
    }

    @Test
    fun `missing accessibility defers presentation until permission is granted`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.updatePermissions(accessibilityGranted = false, overlayGranted = true)
        fixture.coordinator.onServiceConnected()
        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE))

        fixture.coordinator.onForegroundObservation(selectedObservation())
        assertEquals(0, fixture.presentation.showCount)

        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = true)
        assertEquals(1, fixture.presentation.showCount)
    }

    @Test
    fun `missing overlay permission defers presentation until permission is granted`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = false)
        fixture.coordinator.onServiceConnected()
        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE))

        fixture.coordinator.onForegroundObservation(selectedObservation())
        assertEquals(0, fixture.presentation.showCount)

        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = true)
        assertEquals(1, fixture.presentation.showCount)
    }

    @Test
    fun `disconnected service defers presentation until connection`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = true)
        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE))

        fixture.coordinator.onForegroundObservation(selectedObservation())
        assertEquals(0, fixture.presentation.showCount)

        fixture.coordinator.onServiceConnected()
        assertEquals(1, fixture.presentation.showCount)
    }

    @Test
    fun `presentation timeout remains degraded through unrelated ready updates`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        advanceTimeBy(2_500)
        runCurrent()

        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = true)
        fixture.coordinator.onServiceConnected()
        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE, "com.news"))

        assertEquals(
            DegradedReason.PresentationNotConfirmed,
            (fixture.coordinator.health.value as MonitoringHealth.Degraded).reason,
        )
    }

    @Test
    fun `service disconnect and monitored package failure publish closed degraded reasons`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()

        fixture.coordinator.onServiceDisconnected()
        assertEquals(
            DegradedReason.ServiceDisconnected,
            (fixture.coordinator.health.value as MonitoringHealth.Degraded).reason,
        )

        fixture.coordinator.onServiceConnected()
        fixture.coordinator.onMonitoredPackagesUnavailable()
        assertEquals(
            DegradedReason.MonitoredAppsUnavailable,
            (fixture.coordinator.health.value as MonitoringHealth.Degraded).reason,
        )
    }

    @Test
    fun `monitored package initialization retries once then routes to setup review`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onMonitoredPackagesUnavailable()

        fixture.coordinator.retry()

        assertEquals(MonitoringHealth.Initializing, fixture.coordinator.health.value)
        fixture.coordinator.onMonitoredPackagesUnavailable()
        assertEquals(
            MonitoringRecoveryAction.ReviewSetup,
            (fixture.coordinator.health.value as MonitoringHealth.Degraded).recoveryAction,
        )

        fixture.coordinator.retry()
        assertEquals(
            MonitoringRecoveryAction.ReviewSetup,
            (fixture.coordinator.health.value as MonitoringHealth.Degraded).recoveryAction,
        )
    }

    @Test
    fun `TryAgain resets monitoring state and emits a reinitialization request`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation(at = 41L))
        fixture.coordinator.dismissWithBack(fixture.presentation.lastRequest.id)
        val classifierError = IllegalStateException("bad normalized event")
        fixture.coordinator.onClassifierFailure(classifierError)

        fixture.coordinator.retry()

        assertTrue(
            fixture.coordinator.recoveryRequest.value is
                MonitoringRecoveryRequest.ReinitializeMonitoring,
        )
        assertFalse(fixture.approvals.isApproved(SELECTED_PACKAGE))
        assertEquals(MonitoringHealth.Initializing, fixture.coordinator.health.value)

        fixture.coordinator.onMonitoredPackagesReady(emptySet())
        assertEquals(MonitoringHealth.Active(null), fixture.coordinator.health.value)
    }

    @Test
    fun `separate recovery cycles emit distinct reinitialization requests`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onClassifierFailure(IllegalStateException("first failure"))
        fixture.coordinator.retry()
        val firstRequest = fixture.coordinator.recoveryRequest.value
        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE))

        fixture.coordinator.onClassifierFailure(IllegalStateException("later failure"))
        fixture.coordinator.retry()
        val secondRequest = fixture.coordinator.recoveryRequest.value

        assertNotEquals(firstRequest, secondRequest)
    }

    @Test
    fun `classifier failure stays degraded until explicit recovery`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        val classifierError = IllegalArgumentException("malformed observation")

        fixture.coordinator.onClassifierFailure(classifierError)
        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = true)
        fixture.coordinator.onServiceConnected()
        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE))

        assertEquals(
            DegradedReason.ClassifierFailure,
            (fixture.coordinator.health.value as MonitoringHealth.Degraded).reason,
        )
        assertEquals(classifierError, fixture.coordinator.diagnostic.value?.cause)

        fixture.coordinator.retry()
        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE))
        assertEquals(MonitoringHealth.Active(null), fixture.coordinator.health.value)
    }

    @Test
    fun `deferred pause keeps first observation timestamp during reconciliation`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = true)
        fixture.coordinator.onServiceConnected()
        fixture.coordinator.onForegroundObservation(selectedObservation(at = 10L))
        fixture.coordinator.onForegroundObservation(
            ForegroundObservation(
                packageName = "com.news",
                componentName = "com.news.Main",
                observedAtElapsedMs = 20L,
                surface = ForegroundSurface.ELIGIBLE_APP,
            ),
        )

        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE))

        assertEquals(SELECTED_PACKAGE, fixture.presentation.lastRequest.packageName)
        assertEquals(10L, fixture.presentation.lastRequest.observedAtElapsedMs)
    }

    @Test
    fun `monitored package snapshot is isolated from caller mutation`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = true)
        fixture.coordinator.onServiceConnected()
        val packages = mutableSetOf(SELECTED_PACKAGE, "com.news")
        fixture.coordinator.onMonitoredPackagesReady(packages)

        packages.clear()
        fixture.coordinator.onForegroundObservation(selectedObservation())

        assertEquals(1, fixture.presentation.showCount)
    }

    @Test
    fun `package and presentation failures retain their diagnostic causes`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        val loadError = IllegalStateException("database unavailable")

        fixture.coordinator.onMonitoredPackagesUnavailable(loadError)
        assertEquals(
            MonitoringDiagnostic(DegradedReason.MonitoredAppsUnavailable, loadError),
            fixture.coordinator.diagnostic.value,
        )

        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE))
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val windowError = IllegalStateException("window detached")
        fixture.presentation.presentationLost(cause = windowError)
        assertEquals(
            MonitoringDiagnostic(DegradedReason.OverlayUnavailable, windowError),
            fixture.coordinator.diagnostic.value,
        )
    }

    @Test
    fun `stale acknowledgement cannot confirm a newer pending request`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = true)
        fixture.coordinator.onServiceConnected()
        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE, "com.news"))
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val staleRequestId = fixture.presentation.lastRequest.id
        fixture.coordinator.onForegroundObservation(
            ForegroundObservation(
                packageName = "com.news",
                componentName = "com.news.Main",
                observedAtElapsedMs = 2L,
                surface = ForegroundSurface.ELIGIBLE_APP,
            ),
        )
        val currentRequestId = fixture.presentation.lastRequest.id

        fixture.presentation.overlayAdded(staleRequestId)
        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(
            DegradedReason.PresentationNotConfirmed,
            (fixture.coordinator.health.value as MonitoringHealth.Degraded).reason,
        )
        assertEquals(listOf(staleRequestId, currentRequestId), fixture.presentation.dismissedRequestIds)
    }

    @Test
    fun `first selected observation is presented when packages become ready`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = true)
        fixture.coordinator.onServiceConnected()
        fixture.coordinator.onForegroundObservation(selectedObservation())

        assertEquals(0, fixture.presentation.showCount)

        fixture.coordinator.onMonitoredPackagesReady(setOf(SELECTED_PACKAGE))

        assertEquals(1, fixture.presentation.showCount)
        assertEquals(SELECTED_PACKAGE, fixture.presentation.lastRequest.packageName)
    }

    @Test
    fun `opened decision approves visit and delegates exact request data`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val requestId = fixture.presentation.lastRequest.id

        fixture.coordinator.recordOpened(requestId, appName = "Social", reason = "CURIOUS")
        runCurrent()

        assertEquals(
            listOf(
                PauseDecision(
                    requestId = requestId,
                    packageName = SELECTED_PACKAGE,
                    appName = "Social",
                    outcome = DecisionOutcome.OPENED,
                    reason = "CURIOUS",
                ),
            ),
            fixture.decisions.recorded,
        )
        assertTrue(fixture.approvals.isApproved(SELECTED_PACKAGE))
        assertEquals(listOf(requestId), fixture.presentation.dismissedRequestIds)
    }

    @Test
    fun `opened decision waits for durable success before approval and dismissal`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val requestId = fixture.presentation.lastRequest.id
        fixture.decisions.completionGate = CompletableDeferred()

        fixture.coordinator.recordOpened(requestId, appName = "Social", reason = null)
        runCurrent()

        assertEquals(1, fixture.decisions.recorded.size)
        assertTrue(fixture.presentation.dismissedRequestIds.isEmpty())
        assertFalse(fixture.approvals.isApproved(SELECTED_PACKAGE))
        assertEquals(MonitoringHealth.Active(1L), fixture.coordinator.health.value)

        fixture.decisions.completionGate?.complete(Unit)
        runCurrent()

        assertEquals(listOf(requestId), fixture.presentation.dismissedRequestIds)
        assertTrue(fixture.approvals.isApproved(SELECTED_PACKAGE))
    }

    @Test
    fun `failed decision remains visible degraded and cannot be persisted twice`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val requestId = fixture.presentation.lastRequest.id
        val diskFull = IllegalStateException("disk full")
        fixture.decisions.result = Result.failure(diskFull)

        fixture.coordinator.recordOpened(requestId, appName = "Social", reason = null)
        runCurrent()

        assertTrue(fixture.presentation.dismissedRequestIds.isEmpty())
        assertFalse(fixture.approvals.isApproved(SELECTED_PACKAGE))
        assertEquals(
            MonitoringHealth.Degraded(
                DegradedReason.ClassifierFailure,
                MonitoringRecoveryAction.ReviewSetup,
            ),
            fixture.coordinator.health.value,
        )
        assertEquals(diskFull, fixture.coordinator.diagnostic.value?.cause)

        fixture.coordinator.recordDeclined(requestId, appName = "Social", reason = "BORED")
        runCurrent()

        assertEquals(1, fixture.decisions.recorded.size)
    }

    @Test
    fun `cancelled decision job propagates cancellation without publishing success`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val requestId = fixture.presentation.lastRequest.id
        fixture.decisions.cancellation = CancellationException("stopping")

        val job = fixture.coordinator.recordDeclined(requestId, appName = "Social", reason = null)
        runCurrent()

        assertTrue(checkNotNull(job).isCancelled)
        assertTrue(fixture.presentation.dismissedRequestIds.isEmpty())
        assertEquals(MonitoringHealth.Active(1L), fixture.coordinator.health.value)
    }

    @Test
    fun `simultaneous opened and declined callbacks claim one terminal decision`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val requestId = fixture.presentation.lastRequest.id
        fixture.decisions.completionGate = CompletableDeferred()

        fixture.coordinator.recordOpened(requestId, appName = "Social", reason = "CURIOUS")
        fixture.coordinator.recordDeclined(requestId, appName = "Social", reason = "BORED")
        runCurrent()

        assertEquals(1, fixture.decisions.recorded.size)
        assertEquals(DecisionOutcome.OPENED, fixture.decisions.recorded.single().outcome)

        fixture.decisions.completionGate?.complete(Unit)
        runCurrent()
        fixture.coordinator.recordDeclined(requestId, appName = "Social", reason = "BORED")
        runCurrent()

        assertEquals(1, fixture.decisions.recorded.size)
    }

    @Test
    fun `declined decision delegates without approving visit`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val requestId = fixture.presentation.lastRequest.id

        fixture.coordinator.recordDeclined(requestId, appName = "Social", reason = null)
        runCurrent()

        assertEquals(DecisionOutcome.DECLINED, fixture.decisions.recorded.single().outcome)
        assertEquals(requestId, fixture.decisions.recorded.single().requestId)
        assertFalse(fixture.approvals.isApproved(SELECTED_PACKAGE))
        assertEquals(listOf(requestId), fixture.presentation.dismissedRequestIds)
    }

    @Test
    fun `meaningful departure revokes Back approval`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        fixture.coordinator.dismissWithBack(fixture.presentation.lastRequest.id)

        fixture.coordinator.onForegroundObservation(
            ForegroundObservation(
                packageName = "com.news",
                componentName = "com.news.Main",
                observedAtElapsedMs = 2L,
                surface = ForegroundSurface.ELIGIBLE_APP,
            ),
        )

        assertFalse(fixture.approvals.isApproved(SELECTED_PACKAGE))
        assertNull(fixture.presentation.currentRequest)
    }

    private fun fixture(scope: CoroutineScope): Fixture {
        val presentation = RecordingPresentation()
        val decisions = RecordingDecisionSink()
        val approvals = SessionApprovalStore()
        val coordinator = InterventionCoordinator(
            presentation = presentation,
            decisionSink = decisions,
            approvals = approvals,
            appScope = scope,
        )
        return Fixture(coordinator, presentation, decisions, approvals)
    }

    private fun InterventionCoordinator.connectReadyState() {
        updatePermissions(accessibilityGranted = true, overlayGranted = true)
        onServiceConnected()
        onMonitoredPackagesReady(setOf(SELECTED_PACKAGE))
    }

    private fun selectedObservation(at: Long = 1L) = ForegroundObservation(
        packageName = SELECTED_PACKAGE,
        componentName = "$SELECTED_PACKAGE.Main",
        observedAtElapsedMs = at,
        surface = ForegroundSurface.ELIGIBLE_APP,
    )

    private data class Fixture(
        val coordinator: InterventionCoordinator,
        val presentation: RecordingPresentation,
        val decisions: RecordingDecisionSink,
        val approvals: SessionApprovalStore,
    )

    private class RecordingPresentation : PausePresentation {
        private var callbacks: PausePresentationCallbacks? = null
        var currentRequest: PauseRequest? = null
            private set
        val lastRequest: PauseRequest
            get() = checkNotNull(currentRequest)
        var showCount: Int = 0
            private set
        val dismissedRequestIds = mutableListOf<String>()

        override fun show(request: PauseRequest, callbacks: PausePresentationCallbacks) {
            currentRequest = request
            this.callbacks = callbacks
            showCount += 1
        }

        override fun dismiss(requestId: String) {
            dismissedRequestIds += requestId
            if (currentRequest?.id == requestId) currentRequest = null
        }

        fun overlayAdded(requestId: String = lastRequest.id) {
            callbacks?.onOverlayAdded(requestId)
        }

        fun overlayFailed() {
            callbacks?.onOverlayFailed(lastRequest.id, IllegalStateException("overlay refused"))
        }

        fun presentationLost(
            requestId: String = lastRequest.id,
            cause: Throwable = IllegalStateException("window detached"),
        ) {
            callbacks?.onPresentationLost(requestId, cause)
        }
    }

    private class RecordingDecisionSink : PauseDecisionSink {
        val recorded = mutableListOf<PauseDecision>()
        var completionGate: CompletableDeferred<Unit>? = null
        var result: Result<Long> = Result.success(1L)
        var cancellation: CancellationException? = null

        override suspend fun record(decision: PauseDecision): Result<Long> {
            recorded += decision
            cancellation?.let { throw it }
            completionGate?.await()
            return result
        }
    }

    private companion object {
        const val SELECTED_PACKAGE = "com.social"
    }
}

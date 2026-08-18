package com.dgraciano.breathe.service

import com.dgraciano.breathe.data.repository.DecisionOutcome
import com.dgraciano.breathe.data.repository.PauseDecision
import com.dgraciano.breathe.data.repository.PauseDecisionSink
import kotlinx.coroutines.CoroutineScope
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
    fun `presentation loss closes request and offers one retry with a new id`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.coordinator.connectReadyState()
        fixture.coordinator.onForegroundObservation(selectedObservation())
        val firstId = fixture.presentation.lastRequest.id

        fixture.presentation.presentationLost()

        assertEquals(
            MonitoringHealth.Degraded(
                reason = DegradedReason.OverlayUnavailable,
                recoveryAction = MonitoringRecoveryAction.TryAgain,
            ),
            fixture.coordinator.health.value,
        )
        assertEquals(listOf(firstId), fixture.presentation.dismissedRequestIds)

        fixture.coordinator.retry()
        val retryId = fixture.presentation.lastRequest.id

        assertEquals(2, fixture.presentation.showCount)
        assertNotEquals(firstId, retryId)
        fixture.presentation.presentationLost()
        assertEquals(
            MonitoringRecoveryAction.ReviewSetup,
            (fixture.coordinator.health.value as MonitoringHealth.Degraded).recoveryAction,
        )

        fixture.coordinator.retry()
        assertEquals(2, fixture.presentation.showCount)
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

        fun overlayAdded() {
            callbacks?.onOverlayAdded(lastRequest.id)
        }

        fun overlayFailed() {
            callbacks?.onOverlayFailed(lastRequest.id, IllegalStateException("overlay refused"))
        }

        fun presentationLost() {
            callbacks?.onPresentationLost(lastRequest.id, IllegalStateException("window detached"))
        }
    }

    private class RecordingDecisionSink : PauseDecisionSink {
        val recorded = mutableListOf<PauseDecision>()

        override suspend fun record(decision: PauseDecision): Result<Long> {
            recorded += decision
            return Result.success(recorded.size.toLong())
        }
    }

    private companion object {
        const val SELECTED_PACKAGE = "com.social"
    }
}

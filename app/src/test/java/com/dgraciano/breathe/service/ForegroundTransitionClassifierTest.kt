package com.dgraciano.breathe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTransitionClassifierTest {

    private val classifier = ForegroundTransitionClassifier()

    @Test
    fun `deferred selected app reconciles exactly once after packages load`() {
        val waiting = classifier.reduce(emptyState(), observation("com.social")).state

        val ready = classifier.reconcileAfterMonitoredPackagesReady(waiting, setOf("com.social"))

        assertEquals(listOf(ForegroundAction.RequestPause("com.social")), ready.actions)
        assertTrue(classifier.reconcileAfterMonitoredPackagesReady(ready.state, setOf("com.social")).actions.isEmpty())
    }

    @Test
    fun `same app window churn requests one pause for the visit`() {
        val ready = readyState("com.social")
        val first = classifier.reduce(ready, observation("com.social"))

        val churn = classifier.reduce(first.state, observation("com.social", at = 2L))

        assertEquals(listOf(ForegroundAction.RequestPause("com.social")), first.actions)
        assertTrue(churn.actions.isEmpty())
    }

    @Test
    fun `system surface interruption preserves an approved visit`() {
        val first = classifier.reduce(readyState("com.social"), observation("com.social"))
        val approved = classifier.approve(first.state, "com.social")

        val interrupted = classifier.reduce(
            approved,
            observation("com.android.systemui", surface = ForegroundSurface.SYSTEM_UI),
        )

        assertEquals(approved, interrupted.state)
        assertTrue(interrupted.actions.isEmpty())
        assertEquals(ForegroundIgnoreReason.SYSTEM_SURFACE, interrupted.ignoredReason)
    }

    @Test
    fun `meaningful departure revokes the app that was approved`() {
        val first = classifier.reduce(readyState("com.social"), observation("com.social"))
        val approved = classifier.approve(first.state, "com.social")

        val departed = classifier.reduce(approved, observation("com.news"))

        assertEquals(listOf(ForegroundAction.RevokeApproval("com.social")), departed.actions)
        assertEquals("com.news", departed.state.currentEligiblePackage)
    }

    @Test
    fun `returning after meaningful departure requests another pause`() {
        val first = classifier.reduce(readyState("com.social"), observation("com.social"))
        val approved = classifier.approve(first.state, "com.social")
        val elsewhere = classifier.reduce(approved, observation("com.news")).state

        val returned = classifier.reduce(elsewhere, observation("com.social", at = 3L))

        assertEquals(listOf(ForegroundAction.RequestPause("com.social")), returned.actions)
    }

    @Test
    fun `a restarted process may prompt once because approvals are in memory`() {
        val initial = classifier.reduce(readyState("com.social"), observation("com.social"))
        val approvedBeforeRestart = classifier.approve(initial.state, "com.social")
        assertTrue(approvedBeforeRestart.approvedPackages.contains("com.social"))

        val restarted = classifier.reduce(readyState("com.social"), observation("com.social"))

        assertEquals(listOf(ForegroundAction.RequestPause("com.social")), restarted.actions)
    }

    private fun emptyState() = ForegroundClassifierState()

    private fun readyState(vararg packages: String): ForegroundClassifierState =
        classifier.reconcileAfterMonitoredPackagesReady(emptyState(), packages.toSet()).state

    private fun observation(
        packageName: String,
        at: Long = 1L,
        surface: ForegroundSurface = ForegroundSurface.ELIGIBLE_APP,
    ) = ForegroundObservation(
        packageName = packageName,
        componentName = null,
        observedAtElapsedMs = at,
        surface = surface,
    )
}

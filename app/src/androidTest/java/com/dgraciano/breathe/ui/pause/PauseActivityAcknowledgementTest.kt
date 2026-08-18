package com.dgraciano.breathe.ui.pause

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dgraciano.breathe.data.repository.PauseDecision
import com.dgraciano.breathe.data.repository.PauseDecisionSink
import com.dgraciano.breathe.di.PausePresentationModule
import com.dgraciano.breathe.service.ForegroundObservation
import com.dgraciano.breathe.service.ForegroundSurface
import com.dgraciano.breathe.service.DegradedReason
import com.dgraciano.breathe.service.InterventionCoordinator
import com.dgraciano.breathe.service.MonitoringHealth
import com.dgraciano.breathe.service.PausePresentation
import com.dgraciano.breathe.service.PausePresentationCallbacks
import com.dgraciano.breathe.service.PauseRequest
import com.dgraciano.breathe.service.UsageAccessReader
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@UninstallModules(PausePresentationModule::class)
@RunWith(AndroidJUnit4::class)
class PauseActivityAcknowledgementTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue @JvmField val presentation: PausePresentation = RecordingPresentation()

    @BindValue
    @JvmField
    val decisionSink: PauseDecisionSink = object : PauseDecisionSink {
        override suspend fun record(decision: PauseDecision): Result<Long> = Result.success(1L)
    }

    @BindValue
    @JvmField
    val usageAccessReader: UsageAccessReader = object : UsageAccessReader {
        override suspend fun usageByPackage(startMs: Long, endMs: Long) = emptyMap<String, Long>()
        override suspend fun averageSessionMinutes(packageName: String) = 0
    }

    @Inject lateinit var coordinator: InterventionCoordinator
    @Inject lateinit var activityRequests: PauseActivityRequestRegistry

    private val recordingPresentation: RecordingPresentation
        get() = presentation as RecordingPresentation

    @Before
    fun setUp() {
        hiltRule.inject()
        coordinator.updatePermissions(accessibilityGranted = true, overlayGranted = true)
        coordinator.onServiceConnected()
        coordinator.onMonitoredPackagesReady(setOf(TARGET_PACKAGE))
        coordinator.onForegroundObservation(
            ForegroundObservation(
                packageName = TARGET_PACKAGE,
                componentName = "$TARGET_PACKAGE.MainActivity",
                observedAtElapsedMs = 42L,
                surface = ForegroundSurface.ELIGIBLE_APP,
            ),
        )
        activityRequests.open(recordingPresentation.request.id)
    }

    @Test
    fun resumedContentAcknowledgesOnceAndBackDismissesTheSameRequest() {
        val request = recordingPresentation.request
        val context = ApplicationProvider.getApplicationContext<Context>()

        ActivityScenario.launch<PauseActivity>(
            PauseActivity.newIntent(context, request.packageName, request.id),
        ).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(request.id, activity.intent.getStringExtra(PauseActivity.EXTRA_REQUEST_ID))
            }
            assertTrue(activityRequests.isAcknowledged(request.id))
            assertTrue(coordinatorRequestIsConfirmed())

            scenario.recreate()
            scenario.onActivity { activity ->
                activity.startActivity(
                    PauseActivity.newIntent(context, "com.example.retarget", request.id),
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(activityRequests.isAcknowledged(request.id))
            assertFalse(activityRequests.claimAcknowledgement(request.id))
            assertTrue(coordinatorRequestIsConfirmed())

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            assertEquals(listOf(request.id), recordingPresentation.dismissedRequestIds)
            assertTrue(coordinator.health.value is MonitoringHealth.Active)
        }
    }

    @Test
    fun destroyingActivityWithoutAChoiceReportsPresentationLoss() {
        val request = recordingPresentation.request
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<PauseActivity>(
            PauseActivity.newIntent(context, request.packageName, request.id),
        )

        scenario.onActivity { assertTrue(activityRequests.isAcknowledged(request.id)) }
        scenario.close()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val health = coordinator.health.value
        assertTrue(health is MonitoringHealth.Degraded)
        assertEquals(DegradedReason.OverlayUnavailable, (health as MonitoringHealth.Degraded).reason)
        assertEquals(listOf(request.id), recordingPresentation.dismissedRequestIds)
    }

    private fun coordinatorRequestIsConfirmed(): Boolean {
        val pendingField = InterventionCoordinator::class.java.getDeclaredField("pending").apply {
            isAccessible = true
        }
        val pending = pendingField.get(coordinator) ?: return false
        val confirmedField = pending.javaClass.getDeclaredField("confirmed").apply {
            isAccessible = true
        }
        return confirmedField.getBoolean(pending)
    }

    private class RecordingPresentation : PausePresentation {
        lateinit var request: PauseRequest
        val dismissedRequestIds = mutableListOf<String>()

        override fun show(request: PauseRequest, callbacks: PausePresentationCallbacks) {
            this.request = request
        }

        override fun dismiss(requestId: String) {
            dismissedRequestIds += requestId
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.focus"
    }
}

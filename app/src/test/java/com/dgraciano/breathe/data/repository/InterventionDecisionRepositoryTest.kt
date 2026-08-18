package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.InterventionEventDao
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.service.UsageAccessReader
import com.dgraciano.breathe.widget.WidgetRefresher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assert.fail

class InterventionDecisionRepositoryTest {

    private lateinit var dao: InterventionEventDao
    private lateinit var usage: UsageAccessReader
    private lateinit var widget: WidgetRefresher
    private lateinit var sink: PauseDecisionSink

    @Before
    fun setUp() {
        dao = mockk()
        usage = mockk()
        widget = mockk(relaxed = true)
        sink = InterventionDecisionRepository(dao, usage, widget)
    }

    @Test
    fun `decline commits zero before estimate then updates generated id`() = runTest {
        coEvery { dao.insert(any()) } returns 42L
        coEvery { usage.averageSessionMinutes("com.social") } returns 8
        coEvery { dao.updateMinutesSaved(42L, 8) } returns Unit

        val result = sink.record(decision("com.social", DecisionOutcome.DECLINED, null))

        assertEquals(42L, result.getOrThrow())
        coVerifyOrder {
            dao.insert(match {
                it.packageName == "com.social" &&
                    it.appName == "Social" &&
                    it.outcome == InterventionEvent.OUTCOME_DECLINED &&
                    it.reason == null &&
                    it.minutesSaved == 0
            })
            widget.refresh()
            usage.averageSessionMinutes("com.social")
            dao.updateMinutesSaved(42L, 8)
            widget.refresh()
        }
    }

    @Test
    fun `insert failure returns failure without refreshing or reading usage`() = runTest {
        val diskFull = IllegalStateException("disk full")
        coEvery { dao.insert(any()) } throws diskFull

        val result = sink.record(decision("com.social", DecisionOutcome.DECLINED, "BORED"))

        assertTrue(result.isFailure)
        assertEquals(diskFull, result.exceptionOrNull())
        verify(exactly = 0) { widget.refresh() }
        coVerify(exactly = 0) { usage.averageSessionMinutes(any()) }
        coVerify(exactly = 0) { dao.updateMinutesSaved(any(), any()) }
    }

    @Test
    fun `failed estimate keeps committed decision and skips second refresh`() = runTest {
        coEvery { dao.insert(any()) } returns 42L
        coEvery { usage.averageSessionMinutes("com.social") } throws IllegalStateException("usage unavailable")

        val result = sink.record(decision("com.social", DecisionOutcome.DECLINED, null))

        assertEquals(42L, result.getOrThrow())
        coVerify(exactly = 0) { dao.updateMinutesSaved(any(), any()) }
        verify(exactly = 1) { widget.refresh() }
    }

    @Test
    fun `cancelled estimate propagates after the decision is committed`() = runTest {
        coEvery { dao.insert(any()) } returns 42L
        coEvery { usage.averageSessionMinutes("com.social") } throws CancellationException("cancelled")

        try {
            sink.record(decision("com.social", DecisionOutcome.DECLINED, null))
            fail("Expected enrichment cancellation to propagate")
        } catch (_: CancellationException) {
            // The first database commit remains durable, while cooperative cancellation is preserved.
        }

        coVerify(exactly = 0) { dao.updateMinutesSaved(any(), any()) }
        verify(exactly = 1) { widget.refresh() }
    }

    @Test
    fun `opened decision commits without usage enrichment`() = runTest {
        coEvery { dao.insert(any()) } returns 42L

        val result = sink.record(decision("com.social", DecisionOutcome.OPENED, "CURIOUS"))

        assertTrue(result.isSuccess)
        assertEquals(42L, result.getOrThrow())
        coVerify(exactly = 0) { usage.averageSessionMinutes(any()) }
        coVerify(exactly = 0) { dao.updateMinutesSaved(any(), any()) }
        verify(exactly = 1) { widget.refresh() }
    }

    @Test
    fun `failed enrichment update skips second refresh`() = runTest {
        coEvery { dao.insert(any()) } returns 42L
        coEvery { usage.averageSessionMinutes("com.social") } returns 8
        coEvery { dao.updateMinutesSaved(42L, 8) } throws IllegalStateException("database unavailable")

        val result = sink.record(decision("com.social", DecisionOutcome.DECLINED, null))

        assertFalse(result.isFailure)
        assertEquals(42L, result.getOrThrow())
        verify(exactly = 1) { widget.refresh() }
    }

    private fun decision(
        packageName: String,
        outcome: DecisionOutcome,
        reason: String?,
    ) = PauseDecision(
        requestId = "request-1",
        packageName = packageName,
        appName = "Social",
        outcome = outcome,
        reason = reason,
    )
}

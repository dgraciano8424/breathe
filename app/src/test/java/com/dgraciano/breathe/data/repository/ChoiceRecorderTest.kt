package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.PendingChoiceDao
import com.dgraciano.breathe.data.model.PendingChoice
import com.dgraciano.breathe.service.SessionTimeHelper
import com.dgraciano.breathe.widget.WidgetRefresher
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChoiceRecorderTest {
    private val choice = PendingChoice("stable", "com.example", "Example", 123L, "DECLINED")

    @Test fun `failed delivery retains the committed choice and retries without another tap`() = runTest {
        val dao = mockk<PendingChoiceDao>()
        val widget = mockk<WidgetRefresher>(relaxed = true)
        val times = mockk<SessionTimeHelper> { every { getAvgSessionMinutes(any()) } returns 7 }
        var pending: PendingChoice? = null
        var attempts = 0
        coEvery { dao.enqueue(any()) } coAnswers { pending = firstArg() }
        coEvery { dao.next() } coAnswers { pending }
        coEvery { dao.deliver("stable", 7) } coAnswers {
            attempts++
            if (attempts == 1) throw IllegalStateException("temporary storage failure")
            pending = null
            true
        }
        val recorder = ChoiceRecorder(dao, times, widget, backgroundScope, StandardTestDispatcher(testScheduler))
        recorder.enqueue(choice)
        runCurrent()
        assertEquals(choice, pending)
        verify(exactly = 0) { widget.refresh() }
        advanceTimeBy(1_000)
        runCurrent()
        assertNull(pending)
        assertEquals(2, attempts)
        verify(exactly = 1) { widget.refresh() }
    }

    @Test fun `startup recovers an existing choice even if optional usage estimates fail`() = runTest {
        val dao = mockk<PendingChoiceDao>()
        val widget = mockk<WidgetRefresher>(relaxed = true)
        val times = mockk<SessionTimeHelper> { every { getAvgSessionMinutes(any()) } throws IllegalStateException("usage unavailable") }
        var pending: PendingChoice? = choice
        coEvery { dao.next() } coAnswers { pending }
        coEvery { dao.deliver("stable", 20) } coAnswers { pending = null; true }
        val recorder = ChoiceRecorder(dao, times, widget, backgroundScope, StandardTestDispatcher(testScheduler))
        recorder.retryPending()
        runCurrent()
        assertNull(pending)
        coVerify(exactly = 1) { dao.deliver("stable", 20) }
        verify(exactly = 1) { widget.refresh() }
    }

    @Test fun `a choice cleared during estimation is not recreated in history`() = runTest {
        val dao = mockk<PendingChoiceDao>()
        val widget = mockk<WidgetRefresher>(relaxed = true)
        val times = mockk<SessionTimeHelper> { every { getAvgSessionMinutes(any()) } returns 7 }
        coEvery { dao.next() } returnsMany listOf(choice, null)
        coEvery { dao.deliver("stable", 7) } returns false
        val recorder = ChoiceRecorder(dao, times, widget, backgroundScope, StandardTestDispatcher(testScheduler))
        recorder.retryPending()
        runCurrent()
        verify(exactly = 0) { widget.refresh() }
    }
}

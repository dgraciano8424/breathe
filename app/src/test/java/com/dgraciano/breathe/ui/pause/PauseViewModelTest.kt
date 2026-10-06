package com.dgraciano.breathe.ui.pause

import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.MentalHealthTip
import com.dgraciano.breathe.data.repository.MentalHealthTipsRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.service.SessionApprovalStore
import com.dgraciano.breathe.service.PauseClock
import com.dgraciano.breathe.data.repository.ChoiceRecorder
import com.dgraciano.breathe.data.model.PendingChoice
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PauseViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var statsRepo: StatsRepository
    private lateinit var tipsRepo: MentalHealthTipsRepository
    private lateinit var clock: PauseClock
    private var elapsed = 0L
    private lateinit var sessionApprovalStore: SessionApprovalStore
    private lateinit var appRepo: AppRepository
    private lateinit var recorder: ChoiceRecorder
    private lateinit var appScope: CoroutineScope
    private lateinit var viewModel: PauseViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appScope = CoroutineScope(testDispatcher)
        statsRepo = mockk()
        tipsRepo = mockk {
            every { getRandomTip() } returns MentalHealthTip("Ground Yourself", "Feel your feet on the floor.", "ground")
            every { getRandomActivity() } returns "Step outside for 2 minutes"
        }
        elapsed = 0L
        clock = mockk { every { now() } answers { elapsed + testDispatcher.scheduler.currentTime } }
        sessionApprovalStore = mockk(relaxed = true)
        recorder = mockk()
        appRepo = mockk {
            coEvery { getPauseSeconds(any()) } returns BlockedApp.DEFAULT_PAUSE_SECONDS
        }
        viewModel = PauseViewModel(
            statsRepo = statsRepo,
            appRepo = appRepo,
            tipsRepo = tipsRepo,
            clock = clock,
            sessionApprovalStore = sessionApprovalStore,
            choiceRecorder = recorder,
            appScope = appScope
        )
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        appScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `init sets attempt count to dao result plus one`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount("com.example") } returns 2

        viewModel.init("com.example", "Example App")

        assertEquals(3, viewModel.attemptCount.value) // 2 existing + 1 current
    }

    @Test
    fun `init called twice does not accumulate attempt count`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount("com.example") } returns 2

        viewModel.init("com.example", "Example App")
        viewModel.init("com.example", "Example App")

        // Should remain 3, not 4 — init must not stack
        assertEquals(3, viewModel.attemptCount.value)
    }

    @Test
    fun `init with zero existing attempts sets count to one`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0

        viewModel.init("com.example.fresh", "Fresh App")

        assertEquals(1, viewModel.attemptCount.value)
    }

    @Test
    fun `failed attempt count retains the configured duration and releases setup`() = runTest {
        coEvery { appRepo.getPauseSeconds("com.slow") } returns 60
        coEvery { statsRepo.getTodayAttemptCount(any()) } throws IllegalStateException("unavailable")
        viewModel.init("com.slow", "Slow App")
        assertEquals(60, viewModel.pauseSeconds.value)
        assertEquals(1, viewModel.attemptCount.value)
        org.junit.Assert.assertTrue(viewModel.ready.value)
    }

    @Test
    fun `failed duration read falls back without crashing setup`() = runTest {
        coEvery { appRepo.getPauseSeconds(any()) } throws IllegalStateException("unavailable")
        viewModel.init("com.example", "Example App")
        assertEquals(BlockedApp.DEFAULT_PAUSE_SECONDS, viewModel.pauseSeconds.value)
        org.junit.Assert.assertTrue(viewModel.ready.value)
    }

    @Test
    fun `selectReason sets the selected reason`() {
        viewModel.selectReason(InterventionEvent.REASON_BORED)
        assertEquals(InterventionEvent.REASON_BORED, viewModel.selectedReason.value)
    }

    @Test
    fun `selectReason toggles off when same reason selected twice`() {
        viewModel.selectReason(InterventionEvent.REASON_BORED)
        viewModel.selectReason(InterventionEvent.REASON_BORED)
        assertNull(viewModel.selectedReason.value)
    }

    @Test
    fun `selectReason switches to new reason without toggling off`() {
        viewModel.selectReason(InterventionEvent.REASON_BORED)
        viewModel.selectReason(InterventionEvent.REASON_HABIT)
        assertEquals(InterventionEvent.REASON_HABIT, viewModel.selectedReason.value)
    }

    @Test
    fun `recordDeclined records event with DECLINED outcome and current reason`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val slot = slot<PendingChoice>()
        coEvery { recorder.enqueue(capture(slot)) } returns Unit

        viewModel.init("com.example", "Example App")
        viewModel.selectReason(InterventionEvent.REASON_BORED)
        viewModel.recordDeclined()

        val recorded = slot.captured
        assertEquals(InterventionEvent.OUTCOME_DECLINED, recorded.outcome)
        assertEquals("com.example", recorded.packageName)
        assertEquals("Example App", recorded.appName)
        assertEquals(InterventionEvent.REASON_BORED, recorded.reason)
    }

    @Test
    fun `recordDeclined with no reason selected records null reason`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val slot = slot<PendingChoice>()
        coEvery { recorder.enqueue(capture(slot)) } returns Unit

        viewModel.init("com.example", "Example App")
        viewModel.recordDeclined()

        assertNull(slot.captured.reason)
    }

    @Test
    fun `recordOpened records event with OPENED outcome`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val slot = slot<PendingChoice>()
        coEvery { recorder.enqueue(capture(slot)) } returns Unit

        viewModel.init("com.example", "Example App")
        openAfterCountdown()

        assertEquals(InterventionEvent.OUTCOME_OPENED, slot.captured.outcome)
        assertEquals("com.example", slot.captured.packageName)
    }

    @Test
    fun `repeated conflicting choices record only the first choice`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val events = mutableListOf<PendingChoice>()
        coEvery { recorder.enqueue(capture(events)) } returns Unit

        viewModel.init("com.target.app", "Target App")
        viewModel.recordDeclined()
        openAfterCountdown()

        assertEquals(1, events.size)
        assertEquals("com.target.app", events[0].packageName)
        assertEquals(InterventionEvent.OUTCOME_DECLINED, events[0].outcome)
        verify(exactly = 0) { sessionApprovalStore.approve(any()) }
    }

    @Test
    fun `Declined is enqueued before the host destroys its ViewModel`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val slot = slot<PendingChoice>()
        coEvery { recorder.enqueue(capture(slot)) } returns Unit

        viewModel.init("com.example", "Example App")
        // The host can finish after the durable enqueue callback.
        viewModel.recordDeclined()
        viewModel.viewModelScope.cancel()

        assertEquals(InterventionEvent.OUTCOME_DECLINED, slot.captured.outcome)
        assertEquals("com.example", slot.captured.packageName)
        coVerify(exactly = 1) { recorder.enqueue(any()) }
    }

    @Test
    fun `Continue is enqueued before the host destroys its ViewModel`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val slot = slot<PendingChoice>()
        coEvery { recorder.enqueue(capture(slot)) } returns Unit

        viewModel.init("com.example", "Example App")
        openAfterCountdown()
        viewModel.viewModelScope.cancel()

        assertEquals(InterventionEvent.OUTCOME_OPENED, slot.captured.outcome)
        coVerify(exactly = 1) { recorder.enqueue(any()) }
    }

    @Test
    fun `init loads the per-app pause duration`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        coEvery { appRepo.getPauseSeconds("com.slow") } returns 60

        viewModel.init("com.slow", "Slow App")

        assertEquals(60, viewModel.pauseSeconds.value)
    }

    @Test
    fun `init resets the duration so a retargeted pause does not inherit it`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        coEvery { appRepo.getPauseSeconds("com.slow") } returns 60
        coEvery { appRepo.getPauseSeconds("com.quick") } returns 5

        viewModel.init("com.slow", "Slow App")
        viewModel.init("com.quick", "Quick App")

        assertEquals(5, viewModel.pauseSeconds.value)
    }

    @Test
    fun `host closes only after a durable enqueue and duplicates cannot close it again`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val waiting = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { recorder.enqueue(any()) } coAnswers { waiting.await() }
        viewModel.init("com.example", "Example")
        var exits = 0
        viewModel.recordDeclined { exits++ }
        assertEquals(true, viewModel.saving.value)
        assertEquals(0, exits)
        viewModel.recordDeclined { exits++ }
        waiting.complete(Unit)
        assertEquals(1, exits)
        coVerify(exactly = 1) { recorder.enqueue(any()) }
    }

    @Test
    fun `a failed durable write shows retry and does not close or approve the app`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        coEvery { recorder.enqueue(any()) } throws IllegalStateException("disk full")
        viewModel.init("com.example", "Example")
        viewModel.startCountdown()
        elapsed += 60_000
        var exits = 0
        viewModel.recordOpened { exits++ }
        assertEquals(0, exits)
        assertEquals(false, viewModel.saving.value)
        org.junit.Assert.assertNotNull(viewModel.saveError.value)
        verify(exactly = 0) { sessionApprovalStore.approve(any()) }
        coEvery { recorder.enqueue(any()) } returns Unit
        viewModel.recordDeclined { exits++ }
        assertEquals(1, exits)
        assertNull(viewModel.saveError.value)
    }

    @Test
    fun `late save after retargeting persists its original target without closing the new pause`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val waiting = kotlinx.coroutines.CompletableDeferred<Unit>()
        val captured = slot<PendingChoice>()
        coEvery { recorder.enqueue(capture(captured)) } coAnswers { waiting.await() }
        viewModel.init("com.first", "First")
        var exits = 0
        viewModel.recordDeclined { exits++ }
        viewModel.init("com.second", "Second")
        waiting.complete(Unit)
        assertEquals("com.first", captured.captured.packageName)
        assertEquals(0, exits)
        assertEquals(false, viewModel.saving.value)
    }

    @Test
    fun `leaving during enqueue never grants a stale approval or navigates a new window`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val waiting = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { recorder.enqueue(any()) } coAnswers { waiting.await() }
        viewModel.init("com.example", "Example")
        viewModel.startCountdown()
        elapsed += 60_000
        var exits = 0
        viewModel.recordOpened { exits++ }
        viewModel.viewModelScope.cancel()
        waiting.complete(Unit)
        assertEquals(0, exits)
        verify(exactly = 0) { sessionApprovalStore.approve(any()) }
        coVerify(exactly = 1) { recorder.enqueue(any()) }
    }

    @Test
    fun `direct Continue calls cannot bypass the countdown`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        viewModel.init("com.example", "Example")
        viewModel.recordOpened()
        viewModel.startCountdown()
        elapsed = 14_999
        viewModel.recordOpened()
        coVerify(exactly = 0) { recorder.enqueue(any()) }
        verify(exactly = 0) { sessionApprovalStore.approve(any()) }
    }

    private fun openAfterCountdown() {
        viewModel.startCountdown()
        elapsed += 60_000
        viewModel.recordOpened()
    }

    @Test
    fun `recordDeclined captures the target app before a retargeting init can change it`() =
        runTest {
            coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
            val events = mutableListOf<PendingChoice>()
            coEvery { recorder.enqueue(capture(events)) } returns Unit

            viewModel.init("com.first", "First App")
            viewModel.recordDeclined()
            // onNewIntent can retarget the shared ViewModel at any point.
            viewModel.init("com.second", "Second App")

            assertEquals("com.first", events.single().packageName)
            assertEquals("First App", events.single().appName)
        }

    @Test
    fun `a new pause clears its reason and permits a new choice`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val events = mutableListOf<PendingChoice>()
        coEvery { recorder.enqueue(capture(events)) } returns Unit
        viewModel.init("com.first", "First")
        val firstId = viewModel.sessionId.value
        viewModel.selectReason(InterventionEvent.REASON_BORED)
        viewModel.recordDeclined()
        viewModel.init("com.second", "Second")
        assertNull(viewModel.selectedReason.value)
        assertEquals(firstId + 1, viewModel.sessionId.value)
        openAfterCountdown()
        assertEquals(listOf("com.first", "com.second"), events.map { it.packageName })
        assertNull(events[1].reason)
    }

    @Test
    fun `late initialization for the previous app cannot overwrite a new pause`() = runTest {
        val waiting = kotlinx.coroutines.CompletableDeferred<Int>()
        coEvery { appRepo.getPauseSeconds("com.first") } coAnswers {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { waiting.await() }
        }
        coEvery { appRepo.getPauseSeconds("com.second") } returns 5
        coEvery { statsRepo.getTodayAttemptCount("com.first") } returns 90
        coEvery { statsRepo.getTodayAttemptCount("com.second") } returns 2
        viewModel.init("com.first", "First")
        assertEquals(false, viewModel.ready.value)
        viewModel.init("com.second", "Second")
        waiting.complete(60)
        assertEquals(5, viewModel.pauseSeconds.value)
        assertEquals(3, viewModel.attemptCount.value)
        assertEquals(true, viewModel.ready.value)
    }

    @Test
    fun `legitimate intentions remain optional and reset for a new pause`() = runTest {
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        coEvery { recorder.enqueue(any()) } returns Unit
        viewModel.init("com.example", "Example")
        viewModel.selectReason("WORK")
        openAfterCountdown()
        coVerify { recorder.enqueue(match { it.reason == "WORK" && it.outcome == InterventionEvent.OUTCOME_OPENED }) }
        viewModel.init("com.other", "Other")
        assertNull(viewModel.selectedReason.value)
        viewModel.selectReason("RELAX")
        assertEquals("RELAX", viewModel.selectedReason.value)
        viewModel.selectReason("RELAX")
        assertNull(viewModel.selectedReason.value)
    }

    @Test
    fun `unknown reasons cannot replace an explicit intention`() = runTest {
        viewModel.selectReason("LEARN")
        viewModel.selectReason("unexpected")
        assertEquals("LEARN", viewModel.selectedReason.value)
    }
}

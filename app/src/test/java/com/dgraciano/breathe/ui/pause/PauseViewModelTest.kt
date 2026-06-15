package com.dgraciano.breathe.ui.pause

import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.model.Quote
import com.dgraciano.breathe.data.repository.QuoteRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
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
    private lateinit var quoteRepo: QuoteRepository
    private lateinit var statsRepo: StatsRepository
    private lateinit var viewModel: PauseViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        quoteRepo = mockk()
        statsRepo = mockk()
        viewModel = PauseViewModel(quoteRepo, statsRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads quote and sets attempt count to dao result plus one`() = runTest {
        val quote = Quote(text = "Test quote", author = "Author")
        coEvery { quoteRepo.getRandomQuote() } returns quote
        coEvery { statsRepo.getTodayAttemptCount("com.example") } returns 2

        viewModel.init("com.example", "Example App")

        assertEquals(quote, viewModel.quote.value)
        assertEquals(3, viewModel.attemptCount.value) // 2 existing + 1 current
    }

    @Test
    fun `init called twice does not accumulate attempt count`() = runTest {
        val quote = Quote(text = "Test quote", author = "Author")
        coEvery { quoteRepo.getRandomQuote() } returns quote
        coEvery { statsRepo.getTodayAttemptCount("com.example") } returns 2

        viewModel.init("com.example", "Example App")
        viewModel.init("com.example", "Example App")

        // Should remain 3, not 4 — init must not stack
        assertEquals(3, viewModel.attemptCount.value)
    }

    @Test
    fun `init with null quote does not crash`() = runTest {
        coEvery { quoteRepo.getRandomQuote() } returns null
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0

        viewModel.init("com.example", "Example App")

        assertNull(viewModel.quote.value)
        assertEquals(1, viewModel.attemptCount.value)
    }

    @Test
    fun `init with zero existing attempts sets count to one`() = runTest {
        coEvery { quoteRepo.getRandomQuote() } returns null
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0

        viewModel.init("com.example.fresh", "Fresh App")

        assertEquals(1, viewModel.attemptCount.value)
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
        coEvery { quoteRepo.getRandomQuote() } returns null
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val slot = slot<InterventionEvent>()
        coEvery { statsRepo.recordEvent(capture(slot)) } returns Unit

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
        coEvery { quoteRepo.getRandomQuote() } returns null
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val slot = slot<InterventionEvent>()
        coEvery { statsRepo.recordEvent(capture(slot)) } returns Unit

        viewModel.init("com.example", "Example App")
        viewModel.recordDeclined()

        assertNull(slot.captured.reason)
    }

    @Test
    fun `recordOpened records event with OPENED outcome`() = runTest {
        coEvery { quoteRepo.getRandomQuote() } returns null
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val slot = slot<InterventionEvent>()
        coEvery { statsRepo.recordEvent(capture(slot)) } returns Unit

        viewModel.init("com.example", "Example App")
        viewModel.recordOpened()

        assertEquals(InterventionEvent.OUTCOME_OPENED, slot.captured.outcome)
        assertEquals("com.example", slot.captured.packageName)
    }

    @Test
    fun `recordDeclined then recordOpened both use the correct package`() = runTest {
        coEvery { quoteRepo.getRandomQuote() } returns null
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 0
        val events = mutableListOf<InterventionEvent>()
        coEvery { statsRepo.recordEvent(capture(events)) } returns Unit

        viewModel.init("com.target.app", "Target App")
        viewModel.recordDeclined()
        viewModel.recordOpened()

        assertEquals(2, events.size)
        assertEquals("com.target.app", events[0].packageName)
        assertEquals("com.target.app", events[1].packageName)
        assertEquals(InterventionEvent.OUTCOME_DECLINED, events[0].outcome)
        assertEquals(InterventionEvent.OUTCOME_OPENED, events[1].outcome)
    }
}

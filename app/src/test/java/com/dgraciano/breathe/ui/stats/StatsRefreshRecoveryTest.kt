package com.dgraciano.breathe.ui.stats

import com.dgraciano.breathe.data.repository.StatsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsRefreshRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: StatsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk {
            coEvery { getTodayDeclined() } returns 2
            coEvery { getFocusStreak() } returns 1
            coEvery { getTodayMinutesSaved() } returns 3
            coEvery { getTodayTotalAttempts() } returns 5
            coEvery { getWeeklyTotalAttempts() } returns 10
            coEvery { getWeeklyDeclined() } returns 4
            coEvery { getWeeklyMinutesSaved() } returns 12
            coEvery { getTopAppsThisWeek() } returns emptyList()
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `successful load shows complete totals`() = runTest {
        val vm = StatsViewModel(repo, mockk(relaxed = true), mockk(relaxed = true), dispatcher)
        vm.loadStats()
        runCurrent()
        assertFalse(vm.state.value.isLoading)
        assertEquals(5, vm.state.value.todayAttempts)
        assertEquals(3, vm.state.value.todayMinutesSaved)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `failed load leaves loading state and succeeds on retry`() = runTest {
        coEvery { repo.getTodayDeclined() } throws IllegalStateException("database unavailable")
        val vm = StatsViewModel(repo, mockk(relaxed = true), mockk(relaxed = true), dispatcher)
        vm.loadStats()
        runCurrent()
        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.errorMessage)
        coEvery { repo.getTodayDeclined() } returns 2
        vm.loadStats()
        runCurrent()
        assertNull(vm.state.value.errorMessage)
        assertEquals(2, vm.state.value.todayDeclined)
    }

    @Test
    fun `overlapping refreshes perform one read`() = runTest {
        val gate = CompletableDeferred<Int>()
        coEvery { repo.getTodayDeclined() } coAnswers { gate.await() }
        val vm = StatsViewModel(repo, mockk(relaxed = true), mockk(relaxed = true), dispatcher)
        vm.loadStats()
        vm.loadStats()
        runCurrent()
        coVerify(exactly = 1) { repo.getTodayDeclined() }
        gate.complete(2)
        runCurrent()
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `failed refresh preserves the last successful figures`() = runTest {
        val vm = StatsViewModel(repo, mockk(relaxed = true), mockk(relaxed = true), dispatcher)
        vm.loadStats()
        runCurrent()
        coEvery { repo.getWeeklyMinutesSaved() } throws IllegalStateException("read failed")
        vm.loadStats()
        runCurrent()
        assertEquals(5, vm.state.value.todayAttempts)
        assertEquals(12, vm.state.value.weeklyMinutesSaved)
        assertNotNull(vm.state.value.errorMessage)
    }
}

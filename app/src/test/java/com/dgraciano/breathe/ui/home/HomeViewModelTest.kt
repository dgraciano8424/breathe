package com.dgraciano.breathe.ui.home

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.model.Level
import com.dgraciano.breathe.data.model.UserProgress
import com.dgraciano.breathe.data.repository.AchievementRepository
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * These tests exist because `UsageStatsManager` is now injected rather than pulled out of
 * an `@ApplicationContext`. The class was untestable before that change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: AppRepository
    private lateinit var statsRepo: StatsRepository
    private lateinit var achievementRepo: AchievementRepository
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var context: Context
    private lateinit var blockedApps: MutableStateFlow<List<BlockedApp>>

    private fun app(pkg: String) = BlockedApp(packageName = pkg, appName = pkg)

    private fun usage(totalMs: Long): UsageStats =
        mockk { every { totalTimeInForeground } returns totalMs }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        blockedApps = MutableStateFlow(listOf(app("com.a")))

        repo = mockk { every { getBlockedApps() } returns blockedApps }
        statsRepo = mockk {
            coEvery { getTodayTotalAttempts() } returns 0
            coEvery { getTodayDeclined() } returns 0
            coEvery { getTodayMinutesSaved() } returns 0
        }
        achievementRepo = mockk {
            coEvery { getUserProgress() } returns UserProgress(
                totalMinutesSaved = 0,
                lifetimeDeclines = 0,
                currentLevel = Level(0, "Drift", "🌊", "", 0),
                nextLevel = null,
                progressToNext = 0f,
                badges = emptyList()
            )
        }
        usageStatsManager = mockk {
            every { queryAndAggregateUsageStats(any(), any()) } returns
                mapOf("com.a" to usage(120 * 60_000L))
        }
        context = mockk(relaxed = true)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        HomeViewModel(repo, statsRepo, achievementRepo, usageStatsManager, context)

    @Test
    fun `blocked apps are paired with their usage minutes`() = runTest {
        val vm = viewModel()

        val rows = vm.blockedApps.value
        assertEquals(1, rows.size)
        assertEquals("com.a", rows.first().app.packageName)
        assertEquals(120, rows.first().usageMinutes)
    }

    @Test
    fun `an app with no recorded usage reports zero rather than dropping out`() = runTest {
        blockedApps.value = listOf(app("com.a"), app("com.unused"))

        val rows = viewModel().blockedApps.value

        assertEquals(2, rows.size)
        assertEquals(0, rows.single { it.app.packageName == "com.unused" }.usageMinutes)
    }

    /** The regression this change was made for: the aggregate is not re-run per emission. */
    @Test
    fun `changing the blocked list does not re-run the usage aggregate`() = runTest {
        val vm = viewModel()
        verify(exactly = 1) { usageStatsManager.queryAndAggregateUsageStats(any(), any()) }

        blockedApps.value = listOf(app("com.a"), app("com.b"))
        blockedApps.value = listOf(app("com.a"))

        verify(exactly = 1) { usageStatsManager.queryAndAggregateUsageStats(any(), any()) }
        assertEquals(1, vm.blockedApps.value.size)
    }

    @Test
    fun `refreshing stats re-reads usage`() = runTest {
        val vm = viewModel()

        vm.refreshStats()

        verify(exactly = 2) { usageStatsManager.queryAndAggregateUsageStats(any(), any()) }
    }

    /** Usage access is optional, so a refusal must degrade to zeros, not crash the screen. */
    @Test
    fun `missing usage access yields zero minutes instead of propagating`() = runTest {
        every { usageStatsManager.queryAndAggregateUsageStats(any(), any()) } throws
            SecurityException("usage access not granted")

        val rows = viewModel().blockedApps.value

        assertEquals(1, rows.size)
        assertEquals(0, rows.first().usageMinutes)
    }
}

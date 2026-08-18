package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.LifetimeAggregate
import com.dgraciano.breathe.data.db.TodayAggregate
import com.dgraciano.breathe.data.model.Level
import com.dgraciano.breathe.data.model.UserProgress
import com.dgraciano.breathe.ui.achievements.AchievementsViewModel
import com.dgraciano.breathe.ui.home.HomeViewModel
import com.dgraciano.breathe.ui.stats.StatsViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AggregateConsumersTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `Home and Insights share committed insert and enrichment totals`() = runTest {
        val today = MutableStateFlow(TodayAggregate(attempts = 0, declined = 0, minutesSaved = 0))
        val statsRepository = mockk<StatsRepository> {
            every { observeTodayAggregate() } returns today
            coEvery { getWeeklyTotalAttempts() } returns 0
            coEvery { getWeeklyDeclined() } returns 0
            coEvery { getWeeklyMinutesSaved() } returns 0
            coEvery { getFocusStreak() } returns 0
            coEvery { getTopAppsThisWeek() } returns emptyList()
        }
        val achievementRepository = mockk<AchievementRepository> {
            every { observeUserProgress() } returns flowOf(progress(minutes = 0, declines = 0))
        }
        val home = HomeViewModel(
            repo = mockk { every { getBlockedApps() } returns flowOf(emptyList()) },
            statsRepo = statsRepository,
            achievementRepo = achievementRepository,
            usageStatsManager = mockk {
                every { queryAndAggregateUsageStats(any(), any()) } returns emptyMap()
            },
            context = mockk(relaxed = true),
            usageDispatcher = dispatcher,
        )
        val insights = StatsViewModel(statsRepository)

        today.value = TodayAggregate(attempts = 1, declined = 1, minutesSaved = 0)
        advanceUntilIdle()

        assertEquals(1, home.todayAttempts.value)
        assertEquals(1, home.todayDeclined.value)
        assertEquals(0, home.todayMinutesSaved.value)
        assertEquals(1, insights.state.value.todayAttempts)
        assertEquals(1, insights.state.value.todayDeclined)
        assertEquals(0, insights.state.value.todayMinutesSaved)

        today.value = TodayAggregate(attempts = 1, declined = 1, minutesSaved = 12)
        advanceUntilIdle()

        assertEquals(1, home.todayAttempts.value)
        assertEquals(1, home.todayDeclined.value)
        assertEquals(12, home.todayMinutesSaved.value)
        assertEquals(1, insights.state.value.todayAttempts)
        assertEquals(1, insights.state.value.todayDeclined)
        assertEquals(12, insights.state.value.todayMinutesSaved)
    }

    @Test
    fun `Journey maps every lifetime aggregate emission into progress`() = runTest {
        val lifetime = MutableStateFlow(LifetimeAggregate(declined = 1, minutesSaved = 0))
        val statsRepository = mockk<StatsRepository> {
            every { observeLifetimeAggregate() } returns lifetime
        }
        val journey = AchievementsViewModel(AchievementRepository(statsRepository))
        journey.load()
        advanceUntilIdle()

        assertEquals(1L, journey.progress.value?.lifetimeDeclines)
        assertTrue(journey.progress.value!!.badges.single { it.id == "first_breath" }.unlocked)

        lifetime.value = LifetimeAggregate(declined = 12, minutesSaved = 120)
        advanceUntilIdle()

        val updated = journey.progress.value!!
        assertEquals(12L, updated.lifetimeDeclines)
        assertEquals(120L, updated.totalMinutesSaved)
        assertEquals(2, updated.currentLevel.index)
        assertTrue(updated.badges.single { it.id == "ten_strong" }.unlocked)
        assertTrue(updated.badges.single { it.id == "hour_saved" }.unlocked)
        assertFalse(updated.badges.single { it.id == "century" }.unlocked)
    }

    @Test
    fun `Insights keeps one aggregate collector when the screen requests another load`() = runTest {
        val today = MutableStateFlow(TodayAggregate(attempts = 0, declined = 0, minutesSaved = 0))
        val statsRepository = mockk<StatsRepository> {
            every { observeTodayAggregate() } returns today
            coEvery { getWeeklyTotalAttempts() } returns 0
            coEvery { getWeeklyDeclined() } returns 0
            coEvery { getWeeklyMinutesSaved() } returns 0
            coEvery { getFocusStreak() } returns 0
            coEvery { getTopAppsThisWeek() } returns emptyList()
        }
        val insights = StatsViewModel(statsRepository)

        insights.loadStats()
        advanceUntilIdle()

        assertEquals(1, today.subscriptionCount.value)
    }

    @Test
    fun `Journey keeps one lifetime collector across repeated loads`() = runTest {
        val lifetime = MutableStateFlow(LifetimeAggregate(declined = 0, minutesSaved = 0))
        val statsRepository = mockk<StatsRepository> {
            every { observeLifetimeAggregate() } returns lifetime
        }
        val journey = AchievementsViewModel(AchievementRepository(statsRepository))

        journey.load()
        journey.load()
        advanceUntilIdle()

        assertEquals(1, lifetime.subscriptionCount.value)
    }

    private fun progress(minutes: Long, declines: Long) = UserProgress(
        totalMinutesSaved = minutes,
        lifetimeDeclines = declines,
        currentLevel = Level(0, "Seedling", "🌱", "Just getting started", 0),
        nextLevel = Level(1, "Sprout", "🌿", "30 minutes reclaimed", 30),
        progressToNext = 0f,
        badges = emptyList(),
    )
}

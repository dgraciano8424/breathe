package com.dgraciano.breathe.ui.home

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.model.Level
import com.dgraciano.breathe.data.model.UserProgress
import com.dgraciano.breathe.data.repository.AchievementRepository
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.service.MonitoringStatus
import com.dgraciano.breathe.service.SnoozeStore
import com.dgraciano.breathe.data.repository.PausePreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private lateinit var pausePreferences: PausePreferences
    private val reminder = MutableStateFlow("")
    private lateinit var snoozeStore: SnoozeStore
    private val snoozeDeadline = MutableStateFlow(0L)
    private val created = mutableListOf<HomeViewModel>()

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
        reminder.value = ""
        pausePreferences = mockk(relaxed = true) { every { reminder } returns this@HomeViewModelTest.reminder }

        snoozeDeadline.value = 0L
        snoozeStore = mockk(relaxed = true) {
            every { deadline } returns snoozeDeadline
            every { currentDeadline } answers { snoozeDeadline.value }
        }
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        HomeViewModel(repo, statsRepo, achievementRepo, usageStatsManager, context, testDispatcher, MonitoringStatus(), snoozeStore, pausePreferences).also { created.add(it) }

    @Test
    fun `blocked apps are paired with their usage minutes`() = runTest {
        val vm = viewModel()

        val rows = vm.blockedApps.value
        assertEquals(1, rows.size)
        assertEquals("com.a", rows.first().app.packageName)
        assertEquals(120, rows.first().usageMinutes)
    }

    @Test
    fun `an app with no recorded usage reports unknown rather than dropping out`() = runTest {
        blockedApps.value = listOf(app("com.a"), app("com.unused"))

        val rows = viewModel().blockedApps.value

        assertEquals(2, rows.size)
        assertNull(rows.single { it.app.packageName == "com.unused" }.usageMinutes)
    }

    @Test
    fun `a measured sub-minute total is distinct from unavailable usage`() = runTest {
        every { usageStatsManager.queryAndAggregateUsageStats(any(), any()) } returns
            mapOf("com.a" to usage(30_000L))
        assertEquals(0, viewModel().blockedApps.value.single().usageMinutes)
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
    fun `missing usage access yields unknown minutes instead of propagating`() = runTest {
        every { usageStatsManager.queryAndAggregateUsageStats(any(), any()) } throws
            SecurityException("usage access not granted")

        val rows = viewModel().blockedApps.value

        assertEquals(1, rows.size)
        assertNull(rows.first().usageMinutes)
    }

    @Test
    fun `failed refresh retains complete previous figures and recovers on retry`() = runTest {
        coEvery { statsRepo.getTodayTotalAttempts() } returns 4
        coEvery { statsRepo.getTodayDeclined() } returns 2
        coEvery { statsRepo.getTodayMinutesSaved() } returns 10
        val vm = viewModel()
        val feedback = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.feedback.collect { feedback += it } }
        coEvery { statsRepo.getTodayTotalAttempts() } returns 8
        coEvery { statsRepo.getTodayDeclined() } throws IllegalStateException("read failed")
        vm.refreshStats()
        assertEquals(4, vm.todayAttempts.value)
        assertEquals(2, vm.todayDeclined.value)
        assertEquals(10, vm.todayMinutesSaved.value)
        assertEquals(1, feedback.size)
        coEvery { statsRepo.getTodayDeclined() } returns 3
        vm.refreshStats()
        assertEquals(8, vm.todayAttempts.value)
        assertEquals(3, vm.todayDeclined.value)
    }

    @Test
    fun `overlapping home refreshes share one pending statistics read`() = runTest {
        val gate = CompletableDeferred<Int>()
        coEvery { statsRepo.getTodayTotalAttempts() } coAnswers { gate.await() }
        val vm = viewModel()
        vm.refreshStats()
        vm.refreshStats()
        coVerify(exactly = 1) { statsRepo.getTodayTotalAttempts() }
        verify(exactly = 1) { usageStatsManager.queryAndAggregateUsageStats(any(), any()) }
        gate.complete(7)
        assertEquals(7, vm.todayAttempts.value)
        vm.refreshStats()
        coVerify(exactly = 2) { statsRepo.getTodayTotalAttempts() }
    }

    @Test
    fun `failed duration change reports feedback and permits retry`() = runTest {
        coEvery { repo.setPauseSeconds("com.a", 30) } throws IllegalStateException("disk unavailable")
        val vm = viewModel()
        val feedback = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.feedback.collect { feedback += it } }
        vm.setPauseSeconds("com.a", 30)
        assertEquals(1, feedback.size)
        assertTrue(vm.savingPackages.value.isEmpty())
        coEvery { repo.setPauseSeconds("com.a", 30) } returns Unit
        vm.setPauseSeconds("com.a", 30)
        coVerify(exactly = 2) { repo.setPauseSeconds("com.a", 30) }
    }

    @Test
    fun `pending duration change prevents a competing removal of the same app`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { repo.setPauseSeconds("com.a", 30) } coAnswers { gate.await() }
        coEvery { repo.unblockApp(any()) } returns Unit
        val vm = viewModel()
        vm.setPauseSeconds("com.a", 30)
        vm.removeApp(app("com.a"))
        assertEquals(setOf("com.a"), vm.savingPackages.value)
        coVerify(exactly = 0) { repo.unblockApp(any()) }
        gate.complete(Unit)
        assertTrue(vm.savingPackages.value.isEmpty())
        vm.removeApp(app("com.a"))
        coVerify(exactly = 1) { repo.unblockApp(any()) }
    }

    @Test
    fun `failed removal keeps the app visible and releases saving state`() = runTest {
        coEvery { repo.unblockApp(any()) } throws IllegalStateException("disk unavailable")
        val vm = viewModel()
        vm.removeApp(app("com.a"))
        assertEquals("com.a", vm.blockedApps.value.single().app.packageName)
        assertTrue(vm.savingPackages.value.isEmpty())
    }

    @Test
    fun `snooze and resume update the displayed deadline without changing chosen apps`() = runTest {
        coEvery { snoozeStore.snooze(15) } answers { snoozeDeadline.value = 901_000L }
        coEvery { snoozeStore.resume() } answers { snoozeDeadline.value = 0L }
        val vm = viewModel()
        vm.snooze(15).join()
        assertEquals(901_000L, vm.snoozedUntil.value)
        assertEquals("com.a", vm.blockedApps.value.single().app.packageName)
        vm.resumePauses().join()
        assertEquals(0L, vm.snoozedUntil.value)
        assertNull(vm.snoozeError.value)
        coVerify(exactly = 0) { repo.unblockApp(any()) }
    }

    @Test
    fun `failed save reports an error and successful retry clears it`() = runTest {
        val vm = viewModel()
        coEvery { snoozeStore.snooze(15) } throws java.io.IOException("full disk")
        vm.snooze(15).join()
        assertNotNull(vm.snoozeError.value)
        assertEquals(false, vm.snoozeBusy.value)
        assertEquals(0L, vm.snoozedUntil.value)
        coEvery { snoozeStore.snooze(15) } answers { snoozeDeadline.value = 901_000L }
        vm.snooze(15).join()
        assertNull(vm.snoozeError.value)
        assertEquals(901_000L, vm.snoozedUntil.value)
    }

    @Test
    fun `reminder save failure retains its text and successful retry clears the error`() = runTest {
        reminder.value = "A short walk"
        val vm = viewModel()
        coEvery { pausePreferences.saveReminder(any()) } throws java.io.IOException("full disk")
        assertEquals(false, vm.saveReminder("Read a chapter"))
        assertEquals("A short walk", vm.personalReminder.value)
        assertNotNull(vm.reminderError.value)
        assertEquals(false, vm.reminderSaving.value)
        coEvery { pausePreferences.saveReminder(any()) } answers { reminder.value = firstArg() }
        assertEquals(true, vm.saveReminder("Read a chapter"))
        assertEquals("Read a chapter", vm.personalReminder.value)
        assertNull(vm.reminderError.value)
        coVerify(exactly = 0) { repo.unblockApp(any()) }
    }
}

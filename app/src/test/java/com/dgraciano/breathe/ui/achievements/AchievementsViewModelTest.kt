package com.dgraciano.breathe.ui.achievements

import com.dgraciano.breathe.data.model.UserProgress
import com.dgraciano.breathe.data.repository.AchievementRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: AchievementRepository
    private val progress = mockk<UserProgress>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk {
            coEvery { getUserProgress() } returns progress
            every { historyChanges() } returns kotlinx.coroutines.flow.emptyFlow()
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `failed first load stops the spinner and allows retry`() = runTest {
        coEvery { repo.getUserProgress() } throws IllegalStateException("read failed")
        val vm = AchievementsViewModel(repo)
        vm.load()
        runCurrent()
        assertFalse(vm.isLoading.value)
        assertNotNull(vm.errorMessage.value)
        coEvery { repo.getUserProgress() } returns progress
        vm.load()
        runCurrent()
        assertSame(progress, vm.progress.value)
        assertNull(vm.errorMessage.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `concurrent resume and retry requests share one load`() = runTest {
        val gate = CompletableDeferred<UserProgress>()
        coEvery { repo.getUserProgress() } coAnswers { gate.await() }
        val vm = AchievementsViewModel(repo)
        vm.load()
        vm.load()
        runCurrent()
        coVerify(exactly = 1) { repo.getUserProgress() }
        gate.complete(progress)
        runCurrent()
        assertSame(progress, vm.progress.value)
    }

    @Test
    fun `failed refresh keeps the previously loaded journey`() = runTest {
        val vm = AchievementsViewModel(repo)
        vm.load()
        runCurrent()
        coEvery { repo.getUserProgress() } throws IllegalStateException("read failed")
        vm.load()
        runCurrent()
        assertSame(progress, vm.progress.value)
        assertNotNull(vm.errorMessage.value)
        assertFalse(vm.isLoading.value)
    }
}

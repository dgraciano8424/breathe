package com.dgraciano.breathe.ui.appselect

import com.dgraciano.breathe.data.repository.AppRepository
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSelectViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: AppRepository
    private lateinit var loader: InstalledAppsLoader
    private val app = InstalledApp("com.example.social", "Social")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk {
            coEvery { getAllBlockedPackageNames() } returns emptyList()
            coEvery { blockApp(any()) } returns Unit
            coEvery { unblockApp(any()) } returns Unit
        }
        loader = mockk { coEvery { load() } returns listOf(app) }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.observe(vm: AppSelectViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.apps.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.selectedCount.collect() }
    }

    @Test
    fun `rapid duplicate taps write once and show saved state only after success`() = runTest {
        val write = CompletableDeferred<Unit>()
        coEvery { repo.blockApp(any()) } coAnswers { write.await() }
        val vm = AppSelectViewModel(repo, loader)
        observe(vm)
        runCurrent()

        vm.toggleBlock(app)
        vm.toggleBlock(app)
        runCurrent()
        assertEquals(setOf(app.packageName), vm.savingPackages.value)
        assertFalse(vm.apps.value.single().isBlocked)
        coVerify(exactly = 1) { repo.blockApp(any()) }

        write.complete(Unit)
        runCurrent()
        assertTrue(vm.apps.value.single().isBlocked)
        assertEquals(1, vm.selectedCount.value)
        assertTrue(vm.savingPackages.value.isEmpty())
    }

    @Test
    fun `failed save keeps selection unchanged and permits retry`() = runTest {
        coEvery { repo.blockApp(any()) } throws IllegalStateException("disk unavailable")
        val vm = AppSelectViewModel(repo, loader)
        observe(vm)
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.feedback.collect { messages += it } }
        runCurrent()

        vm.toggleBlock(app)
        runCurrent()
        assertFalse(vm.apps.value.single().isBlocked)
        assertTrue(vm.savingPackages.value.isEmpty())
        assertTrue(messages.single().contains("Social"))

        coEvery { repo.blockApp(any()) } returns Unit
        vm.toggleBlock(app)
        runCurrent()
        assertTrue(vm.apps.value.single().isBlocked)
    }

    @Test
    fun `stale click callback uses the current saved state`() = runTest {
        val vm = AppSelectViewModel(repo, loader)
        observe(vm)
        runCurrent()
        vm.toggleBlock(app)
        runCurrent()
        vm.toggleBlock(app)
        runCurrent()

        coVerify(exactly = 1) { repo.blockApp(any()) }
        coVerify(exactly = 1) { repo.unblockApp(any()) }
        assertFalse(vm.apps.value.single().isBlocked)
    }

    @Test
    fun `search does not change the total monitored count`() = runTest {
        coEvery { repo.getAllBlockedPackageNames() } returns listOf(app.packageName)
        val vm = AppSelectViewModel(repo, loader)
        observe(vm)
        runCurrent()
        vm.onSearchQueryChanged("no match")
        runCurrent()
        assertTrue(vm.apps.value.isEmpty())
        assertEquals(1, vm.selectedCount.value)
    }

    @Test
    fun `reload is ignored while a selection is saving`() = runTest {
        val write = CompletableDeferred<Unit>()
        coEvery { repo.blockApp(any()) } coAnswers { write.await() }
        val vm = AppSelectViewModel(repo, loader)
        runCurrent()
        vm.toggleBlock(app)
        vm.loadInstalledApps()
        runCurrent()
        coVerify(exactly = 1) { loader.load() }
        write.complete(Unit)
        runCurrent()
    }

    @Test
    fun `load failure offers retry and successful retry clears the error`() = runTest {
        coEvery { loader.load() } throws IllegalStateException("package manager unavailable")
        val vm = AppSelectViewModel(repo, loader)
        observe(vm)
        runCurrent()
        assertNotNull(vm.errorMessage.value)
        assertFalse(vm.isLoading.value)

        coEvery { loader.load() } returns listOf(app)
        vm.loadInstalledApps()
        runCurrent()
        assertNull(vm.errorMessage.value)
        assertEquals(app.packageName, vm.apps.value.single().packageName)
    }
}

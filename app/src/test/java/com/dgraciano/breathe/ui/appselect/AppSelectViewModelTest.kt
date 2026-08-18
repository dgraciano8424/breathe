package com.dgraciano.breathe.ui.appselect

import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.service.UsageAccessReader
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class AppSelectViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: AppRepository
    private lateinit var launcherApps: LauncherApps
    private lateinit var usage: UsageAccessReader

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk {
            coEvery { getAllBlockedPackageNames() } returns emptyList()
        }
        launcherApps = mockk()
        usage = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun app(name: String, packageName: String = "com.example.${name.lowercase()}") =
        LaunchableApp(packageName = packageName, appName = name)

    private fun viewModel(dispatcher: CoroutineDispatcher = testDispatcher) = AppSelectViewModel(
        repo = repo,
        launcherApps = launcherApps,
        usage = usage,
        appLoadDispatcher = dispatcher,
    )

    /** Models a package-manager call that returns after cancellation has already been requested. */
    private suspend fun <T> awaitIgnoringCancellation(value: CompletableDeferred<T>): T =
        suspendCoroutine { continuation ->
            value.invokeOnCompletion { failure ->
                if (failure == null) continuation.resume(value.getCompleted())
                else continuation.resumeWithException(failure)
            }
        }

    /** Removing the security fallback would make denied access hide every launchable app. */
    @Test
    fun `denied usage access still shows launchable apps alphabetically`() = runTest {
        coEvery { usage.usageByPackage(any(), any()) } throws SecurityException()
        coEvery { launcherApps.load() } returns listOf(app("Beta"), app("Alpha"))

        val viewModel = viewModel()
        viewModel.loadInstalledApps()

        assertEquals(
            listOf("Alpha", "Beta"),
            viewModel.apps.first { it.size == 2 }.map { it.appName },
        )
        assertNull(viewModel.errorMessage.value)
    }

    /** Removing either tie breaker would make equally-used entries fluctuate between reloads. */
    @Test
    fun `usage ordering breaks ties by case insensitive name then package`() = runTest {
        coEvery { usage.usageByPackage(any(), any()) } returns mapOf(
            "com.example.zebra" to 120 * 60_000L,
            "com.example.alpha" to 120 * 60_000L,
            "com.example.alpha-a" to 60_000L,
            "com.example.alpha-b" to 60_000L,
        )
        coEvery { launcherApps.load() } returns listOf(
            app("Zebra", "com.example.zebra"),
            app("alpha", "com.example.alpha"),
            app("Alpha", "com.example.alpha-b"),
            app("Alpha", "com.example.alpha-a"),
        )

        val viewModel = viewModel()

        assertEquals(
            listOf(
                "com.example.alpha",
                "com.example.zebra",
                "com.example.alpha-a",
                "com.example.alpha-b",
            ),
            viewModel.apps.first { it.size == 4 }.map { it.packageName },
        )
    }

    /** Removing the current-load guard lets a late, cancelled lookup replace newer picker rows. */
    @Test
    fun `newer load owns app rows when older lookup finishes later`() = runTest {
        val older = CompletableDeferred<List<LaunchableApp>>()
        val newer = CompletableDeferred<List<LaunchableApp>>()
        var calls = 0
        coEvery { launcherApps.load() } coAnswers {
            if (calls++ == 0) awaitIgnoringCancellation(older) else newer.await()
        }
        coEvery { usage.usageByPackage(any(), any()) } returns emptyMap()
        val viewModel = viewModel(StandardTestDispatcher(testScheduler))
        runCurrent()

        viewModel.loadInstalledApps()
        runCurrent()
        newer.complete(listOf(app("Beta")))
        runCurrent()
        older.complete(listOf(app("Alpha")))
        runCurrent()

        assertEquals("Beta", viewModel.apps.first { it.size == 1 }.single().appName)
        assertEquals(false, viewModel.isLoading.value)
    }

    /** Removing the stale-finally guard lets an obsolete request hide a newer request's spinner. */
    @Test
    fun `older load finishing cannot clear loading while newer load is pending`() = runTest {
        val older = CompletableDeferred<List<LaunchableApp>>()
        val newer = CompletableDeferred<List<LaunchableApp>>()
        var calls = 0
        coEvery { launcherApps.load() } coAnswers {
            if (calls++ == 0) awaitIgnoringCancellation(older) else newer.await()
        }
        coEvery { usage.usageByPackage(any(), any()) } returns emptyMap()
        val viewModel = viewModel(StandardTestDispatcher(testScheduler))
        runCurrent()

        viewModel.loadInstalledApps()
        runCurrent()
        older.complete(listOf(app("Alpha")))
        runCurrent()

        assertEquals(true, viewModel.isLoading.value)

        newer.complete(listOf(app("Beta")))
        runCurrent()
    }

    /** Catching cancellation as an ordinary failure would incorrectly show the retry message. */
    @Test
    fun `cancellation does not publish a picker error`() = runTest {
        coEvery { launcherApps.load() } throws CancellationException("picker was replaced")

        val viewModel = viewModel()

        assertNull(viewModel.errorMessage.value)
    }
}

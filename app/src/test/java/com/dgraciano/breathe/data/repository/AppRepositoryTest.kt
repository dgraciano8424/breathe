package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.BlockedAppDao
import com.dgraciano.breathe.data.model.BlockedApp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppRepositoryTest {

    private lateinit var dao: BlockedAppDao
    private lateinit var repo: AppRepository

    @Before
    fun setUp() {
        dao = mockk()
        repo = AppRepository(dao)
    }

    @Test
    fun `getBlockedApps returns flow from dao`() = runTest {
        val apps = listOf(BlockedApp(packageName = "com.example", appName = "Example"))
        every { dao.getAll() } returns flowOf(apps)

        val result = repo.getBlockedApps().first()

        assertEquals(apps, result)
        verify { dao.getAll() }
    }

    @Test
    fun `getBlockedApps returns empty flow when no apps blocked`() = runTest {
        every { dao.getAll() } returns flowOf(emptyList())

        val result = repo.getBlockedApps().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `isBlocked returns true when package is blocked`() = runTest {
        coEvery { dao.isBlocked("com.example") } returns true

        assertTrue(repo.isBlocked("com.example"))
    }

    @Test
    fun `isBlocked returns false when package is not blocked`() = runTest {
        coEvery { dao.isBlocked("com.unknown") } returns false

        assertFalse(repo.isBlocked("com.unknown"))
    }

    @Test
    fun `blockApp delegates to dao insert`() = runTest {
        val app = BlockedApp(packageName = "com.example", appName = "Example")
        coEvery { dao.insert(app) } returns Unit

        repo.blockApp(app)

        coVerify(exactly = 1) { dao.insert(app) }
    }

    @Test
    fun `unblockApp delegates to dao delete`() = runTest {
        val app = BlockedApp(packageName = "com.example", appName = "Example")
        coEvery { dao.delete(app) } returns Unit

        repo.unblockApp(app)

        coVerify(exactly = 1) { dao.delete(app) }
    }

    @Test
    fun `getAllBlockedPackageNames returns list from dao`() = runTest {
        val packages = listOf("com.example", "com.other.app", "com.social.network")
        coEvery { dao.getAllPackageNames() } returns packages

        val result = repo.getAllBlockedPackageNames()

        assertEquals(packages, result)
    }

    @Test
    fun `getAllBlockedPackageNames returns empty list when nothing blocked`() = runTest {
        coEvery { dao.getAllPackageNames() } returns emptyList()

        val result = repo.getAllBlockedPackageNames()

        assertTrue(result.isEmpty())
    }
}

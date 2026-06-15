package com.dgraciano.breathe.service

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ForegroundAppDetectorTest {

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var detector: ForegroundAppDetector

    @Before
    fun setUp() {
        usageStatsManager = mockk()
        detector = ForegroundAppDetector(usageStatsManager)
    }

    @Test
    fun `returns package of app with highest lastTimeUsed`() {
        val older = mockk<UsageStats>().apply {
            every { lastTimeUsed } returns 1000L
            every { packageName } returns "com.old.app"
        }
        val newer = mockk<UsageStats>().apply {
            every { lastTimeUsed } returns 5000L
            every { packageName } returns "com.current.app"
        }
        every { usageStatsManager.queryUsageStats(any(), any(), any()) } returns listOf(older, newer)

        assertEquals("com.current.app", detector.getCurrentApp())
    }

    @Test
    fun `returns null when stats list is empty`() {
        every { usageStatsManager.queryUsageStats(any(), any(), any()) } returns emptyList()

        assertNull(detector.getCurrentApp())
    }

    @Test
    fun `returns null when queryUsageStats returns null`() {
        every { usageStatsManager.queryUsageStats(any(), any(), any()) } returns null

        assertNull(detector.getCurrentApp())
    }

    @Test
    fun `returns single app when only one stat returned`() {
        val stat = mockk<UsageStats>().apply {
            every { lastTimeUsed } returns 9999L
            every { packageName } returns "com.only.app"
        }
        every { usageStatsManager.queryUsageStats(any(), any(), any()) } returns listOf(stat)

        assertEquals("com.only.app", detector.getCurrentApp())
    }

    @Test
    fun `returns app with highest time when three apps present`() {
        val stats = listOf(
            mockk<UsageStats>().apply {
                every { lastTimeUsed } returns 3000L
                every { packageName } returns "com.middle.app"
            },
            mockk<UsageStats>().apply {
                every { lastTimeUsed } returns 1000L
                every { packageName } returns "com.oldest.app"
            },
            mockk<UsageStats>().apply {
                every { lastTimeUsed } returns 9000L
                every { packageName } returns "com.newest.app"
            }
        )
        every { usageStatsManager.queryUsageStats(any(), any(), any()) } returns stats

        assertEquals("com.newest.app", detector.getCurrentApp())
    }
}

package com.dgraciano.breathe.ui.appselect

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InstalledAppsLoaderTest {
    private lateinit var context: Context
    private lateinit var pm: PackageManager
    private lateinit var usage: UsageStatsManager

    private fun launcher(pkg: String, label: String): ResolveInfo = mockk<ResolveInfo> {
        every { loadLabel(any()) } returns label
        every { loadIcon(any()) } returns null
    }.apply { activityInfo = ActivityInfo().apply { packageName = pkg } }

    @Before
    fun setUp() {
        pm = mockk {
            every { queryIntentActivities(any(), 0) } returns listOf(
                launcher("com.z", "Zulu"), launcher("com.a", "Alpha"),
                launcher("com.a", "Duplicate activity"), launcher("com.breathe", "Breathe")
            )
        }
        context = mockk {
            every { packageManager } returns pm
            every { packageName } returns "com.breathe"
        }
        usage = mockk { every { queryAndAggregateUsageStats(any(), any()) } returns emptyMap() }
    }

    @Test
    fun `denied optional usage access still lists unique apps alphabetically`() = runTest {
        every { usage.queryAndAggregateUsageStats(any(), any()) } throws SecurityException("denied")
        val apps = InstalledAppsLoader(context, usage).load()
        assertEquals(listOf("com.a", "com.z"), apps.map { it.packageName })
        assertTrue(apps.all { it.usageTimeMinutes == null })
    }

    @Test
    fun `known usage sorts first while missing data remains unknown`() = runTest {
        val stat = mockk<UsageStats> { every { totalTimeInForeground } returns 120_000L }
        every { usage.queryAndAggregateUsageStats(any(), any()) } returns mapOf("com.z" to stat)
        val apps = InstalledAppsLoader(context, usage).load()
        assertEquals("com.z", apps.first().packageName)
        assertEquals(2, apps.first().usageTimeMinutes)
        assertNull(apps.last().usageTimeMinutes)
    }
}

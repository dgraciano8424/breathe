package com.dgraciano.breathe.service

import android.app.usage.UsageStatsManager
import javax.inject.Inject

class ForegroundAppDetector @Inject constructor(
    private val usageStatsManager: UsageStatsManager
) {
    fun getCurrentApp(): String? {
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 10_000L,
            now
        )
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}

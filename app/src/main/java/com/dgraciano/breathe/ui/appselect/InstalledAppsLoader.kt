package com.dgraciano.breathe.ui.appselect

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Optional usage information must never prevent someone choosing apps to pause. */
class InstalledAppsLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageStatsManager: UsageStatsManager
) {
    suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val stats = runCatching {
            usageStatsManager.queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(7), now)
        }.getOrDefault(emptyMap())
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                InstalledApp(
                    packageName = pkg,
                    appName = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                    usageTimeMinutes = stats[pkg]?.let { (it.totalTimeInForeground / 60000).toInt() }
                )
            }
            .sortedWith(compareByDescending<InstalledApp> { it.usageTimeMinutes ?: 0 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
                .thenBy { it.packageName })
    }
}

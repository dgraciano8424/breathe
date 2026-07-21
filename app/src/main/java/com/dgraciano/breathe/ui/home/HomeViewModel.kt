package com.dgraciano.breathe.ui.home

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.service.AppMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class BlockedAppWithStats(
    val app: BlockedApp,
    val usageMinutes: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: AppRepository,
    private val statsRepo: StatsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _blockedAppsWithStats = MutableStateFlow<List<BlockedAppWithStats>>(emptyList())
    val blockedApps: StateFlow<List<BlockedAppWithStats>> = _blockedAppsWithStats

    private val _todayAttempts = MutableStateFlow(0)
    val todayAttempts: StateFlow<Int> = _todayAttempts

    private val _todayDeclined = MutableStateFlow(0)
    val todayDeclined: StateFlow<Int> = _todayDeclined

    init {
        startService()
        refreshStats()
        loadAppsWithStats()
    }

    private fun loadAppsWithStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val start = now - TimeUnit.DAYS.toMillis(7)
            
            repo.getBlockedApps().collect { apps ->
                val stats = usageStatsManager.queryAndAggregateUsageStats(start, now)
                _blockedAppsWithStats.value = apps.map { app ->
                    val timeMs = stats[app.packageName]?.totalTimeInForeground ?: 0L
                    BlockedAppWithStats(app, (timeMs / 60000).toInt())
                }
            }
        }
    }

    fun removeApp(app: BlockedApp) = viewModelScope.launch { repo.unblockApp(app) }

    fun startService() = AppMonitorService.start(context)

    fun refreshStats() {
        viewModelScope.launch {
            _todayAttempts.value = statsRepo.getTodayTotalAttempts()
            _todayDeclined.value = statsRepo.getTodayDeclined()
        }
    }
}

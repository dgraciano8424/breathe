package com.dgraciano.breathe.ui.home

import android.app.usage.UsageStatsManager
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.model.UserProgress
import com.dgraciano.breathe.data.repository.AchievementRepository
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.service.BreatheAccessibilityService
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
    private val achievementRepo: AchievementRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _blockedAppsWithStats = MutableStateFlow<List<BlockedAppWithStats>>(emptyList())
    val blockedApps: StateFlow<List<BlockedAppWithStats>> = _blockedAppsWithStats

    private val _todayAttempts = MutableStateFlow(0)
    val todayAttempts: StateFlow<Int> = _todayAttempts

    private val _todayDeclined = MutableStateFlow(0)
    val todayDeclined: StateFlow<Int> = _todayDeclined

    private val _todayMinutesSaved = MutableStateFlow(0)
    val todayMinutesSaved: StateFlow<Int> = _todayMinutesSaved

    private val _progress = MutableStateFlow<UserProgress?>(null)
    val progress: StateFlow<UserProgress?> = _progress

    private val _nimbusStrength = MutableStateFlow(1)
    val nimbusStrength: StateFlow<Int> = _nimbusStrength

    /** False when the accessibility service is off or overlay permission was revoked. */
    private val _isMonitoringActive = MutableStateFlow(false)
    val isMonitoringActive: StateFlow<Boolean> = _isMonitoringActive

    init {
        refreshMonitoringState()
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

    /** The blocked-app Flow re-emits, so the row updates without extra plumbing. */
    fun setPauseSeconds(packageName: String, seconds: Int) = viewModelScope.launch {
        repo.setPauseSeconds(packageName, seconds)
    }

    /**
     * The accessibility service is bound by the system, so there is nothing to start.
     * What the UI needs instead is whether it is actually running — a revoked permission
     * previously left every screen silently showing zeros.
     */
    fun refreshMonitoringState() {
        _isMonitoringActive.value =
            BreatheAccessibilityService.isEnabled(context) && Settings.canDrawOverlays(context)
    }

    fun refreshStats() {
        viewModelScope.launch {
            _todayAttempts.value = statsRepo.getTodayTotalAttempts()
            _todayDeclined.value = statsRepo.getTodayDeclined()
            _todayMinutesSaved.value = statsRepo.getTodayMinutesSaved()
            val userProgress = achievementRepo.getUserProgress()
            _progress.value = userProgress
            _nimbusStrength.value = userProgress.currentLevel.index + 1
        }
    }
}

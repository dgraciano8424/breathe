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
    private val usageStatsManager: UsageStatsManager,
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

    /**
     * Per-package foreground minutes over the last 7 days, refreshed on its own schedule
     * rather than recomputed whenever the blocked list changes.
     */
    private val _usageMinutes = MutableStateFlow<Map<String, Int>>(emptyMap())

    init {
        refreshMonitoringState()
        refreshStats()
        loadAppsWithStats()
    }

    /**
     * Combines the blocked-apps Flow with the usage totals instead of aggregating inside
     * the collector. The aggregate covers every app on the device and does not depend on
     * which row changed, so re-running it on each emission meant a full 7-day scan every
     * time the user added, removed, or re-timed a single app.
     */
    private fun loadAppsWithStats() {
        viewModelScope.launch {
            combine(repo.getBlockedApps(), _usageMinutes) { apps, usage ->
                apps.map { app -> BlockedAppWithStats(app, usage[app.packageName] ?: 0) }
            }.collect { _blockedAppsWithStats.value = it }
        }
    }

    /**
     * Returns empty rather than throwing when usage access is absent — the permission is
     * optional, so a missing grant means "no times to show", not a failure.
     */
    private fun refreshUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val start = now - TimeUnit.DAYS.toMillis(7)
            _usageMinutes.value = runCatching {
                usageStatsManager.queryAndAggregateUsageStats(start, now)
                    .mapValues { (_, stat) -> (stat.totalTimeInForeground / 60000).toInt() }
            }.getOrDefault(emptyMap())
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
        refreshUsage()
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

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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import com.dgraciano.breathe.di.IoDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class BlockedAppWithStats(
    val app: BlockedApp,
    val usageMinutes: Int?
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: AppRepository,
    private val statsRepo: StatsRepository,
    private val achievementRepo: AchievementRepository,
    private val usageStatsManager: UsageStatsManager,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
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

    private val _savingPackages = MutableStateFlow<Set<String>>(emptySet())
    val savingPackages: StateFlow<Set<String>> = _savingPackages
    private val messages = Channel<String>(Channel.BUFFERED)
    val feedback = messages.receiveAsFlow()
    private var statsJob: Job? = null

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
                apps.map { app -> BlockedAppWithStats(app, usage[app.packageName]) }
            }.collect { _blockedAppsWithStats.value = it }
        }
    }

    /**
     * Returns empty rather than throwing when usage access is absent — the permission is
     * optional, so a missing grant means "no times to show", not a failure.
     */
    private fun refreshUsage() {
        viewModelScope.launch(ioDispatcher) {
            val now = System.currentTimeMillis()
            val start = now - TimeUnit.DAYS.toMillis(7)
            _usageMinutes.value = runCatching {
                usageStatsManager.queryAndAggregateUsageStats(start, now)
                    .mapValues { (_, stat) -> (stat.totalTimeInForeground / 60000).toInt() }
            }.getOrDefault(emptyMap())
        }
    }

    fun removeApp(app: BlockedApp) = changeApp(app.packageName) { repo.unblockApp(app) }

    /** The blocked-app Flow re-emits, so the row updates without extra plumbing. */
    fun setPauseSeconds(packageName: String, seconds: Int) {
        if (seconds !in BlockedApp.PAUSE_OPTIONS) return
        changeApp(packageName) { repo.setPauseSeconds(packageName, seconds) }
    }

    private fun changeApp(packageName: String, write: suspend () -> Unit) {
        if (packageName in _savingPackages.value) return
        _savingPackages.update { it + packageName }
        viewModelScope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messages.send("Couldn't save that app change. Your previous setting is still in place. Try again.")
            } finally {
                _savingPackages.update { it - packageName }
            }
        }
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
        if (statsJob?.isActive == true) return
        refreshUsage()
        statsJob = viewModelScope.launch {
            try {
                val attempts = statsRepo.getTodayTotalAttempts()
                val declined = statsRepo.getTodayDeclined()
                val minutesSaved = statsRepo.getTodayMinutesSaved()
                val userProgress = achievementRepo.getUserProgress()
                // Keep the last complete result if any of the reads fail.
                _todayAttempts.value = attempts
                _todayDeclined.value = declined
                _todayMinutesSaved.value = minutesSaved
                _progress.value = userProgress
                _nimbusStrength.value = userProgress.currentLevel.index + 1
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messages.send("Couldn't refresh your home insights. Reopen this screen to try again.")
            }
        }
    }
}

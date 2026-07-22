package com.dgraciano.breathe.ui.appselect

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val usageTimeMinutes: Int = 0,
    val isBlocked: Boolean = false
)

@HiltViewModel
class AppSelectViewModel @Inject constructor(
    private val repo: AppRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    
    val apps: StateFlow<List<InstalledApp>> = combine(_allApps, _searchQuery) { all, query ->
        if (query.isBlank()) all
        else all.filter { it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { loadInstalledApps() }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val alreadyBlocked = repo.getAllBlockedPackageNames().toSet()
            
            // Get stats for the last 7 days
            val now = System.currentTimeMillis()
            val start = now - TimeUnit.DAYS.toMillis(7)
            val stats = usageStatsManager.queryAndAggregateUsageStats(start, now)
            
            // Find ALL apps that have a launcher activity (user-facing apps)
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            
            val installed = resolveInfos.map { info ->
                val packageName = info.activityInfo.packageName
                val totalTime = stats[packageName]?.totalTimeInForeground ?: 0L
                InstalledApp(
                    packageName = packageName,
                    appName = info.loadLabel(pm).toString(),
                    icon = try { info.loadIcon(pm) } catch (e: Exception) { null },
                    usageTimeMinutes = (totalTime / 60000).toInt(),
                    isBlocked = packageName in alreadyBlocked
                )
            }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedByDescending { it.usageTimeMinutes }
            
            _allApps.value = installed
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleBlock(app: InstalledApp) = viewModelScope.launch {
        if (app.isBlocked) {
            repo.unblockApp(BlockedApp(packageName = app.packageName, appName = app.appName))
        } else {
            repo.blockApp(BlockedApp(packageName = app.packageName, appName = app.appName))
        }
        
        // Update local list to toggle blocked state immediately
        _allApps.update { current -> 
            current.map { 
                if (it.packageName == app.packageName) it.copy(isBlocked = !app.isBlocked) 
                else it
            }
        }
    }
}

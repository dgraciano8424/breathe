package com.dgraciano.breathe.ui.appselect

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.di.IoDispatcher
import com.dgraciano.breathe.service.UsageAccessReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
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

/** A launcher entry is available without Usage Access, unlike its usage estimate. */
data class LaunchableApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
)

/**
 * Isolates package-manager enumeration from the picker so optional Usage Access can never
 * prevent somebody from choosing an otherwise launchable app.
 */
class LauncherApps @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun load(): List<LaunchableApp> {
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)

        return packageManager.queryIntentActivities(mainIntent, 0)
            .map { info ->
                LaunchableApp(
                    packageName = info.activityInfo.packageName,
                    appName = info.loadLabel(packageManager).toString(),
                    icon = runCatching { info.loadIcon(packageManager) }.getOrNull(),
                )
            }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
    }
}

@HiltViewModel
class AppSelectViewModel @Inject constructor(
    private val repo: AppRepository,
    private val launcherApps: LauncherApps,
    private val usage: UsageAccessReader,
    @param:IoDispatcher private val appLoadDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    
    val apps: StateFlow<List<InstalledApp>> = combine(_allApps, _searchQuery) { all, query ->
        if (query.isBlank()) all
        else all.filter { it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Number of apps currently marked as distractions, independent of the search filter. */
    val selectedCount: StateFlow<Int> = _allApps
        .map { list -> list.count { it.isBlocked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Explicit rather than inferred from an empty list, which cannot tell the
     *  difference between "still loading", "nothing installed", and "the query failed". */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init { loadInstalledApps() }

    fun loadInstalledApps() {
        viewModelScope.launch(appLoadDispatcher) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _allApps.value = queryInstalledApps()
            } catch (e: Exception) {
                Log.e(TAG, "Could not list installed apps", e)
                _errorMessage.value = "Couldn't load your apps. Pull to try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun queryInstalledApps(): List<InstalledApp> {
        // Enumeration is the primary operation. Usage access is only an optional enrichment.
        val launchableApps = launcherApps.load()
        val alreadyBlocked = repo.getAllBlockedPackageNames().toSet()
        val now = System.currentTimeMillis()
        val start = now - TimeUnit.DAYS.toMillis(7)
        val usageByPackage = try {
            usage.usageByPackage(start, now)
        } catch (_: SecurityException) {
            emptyMap()
        }

        return launchableApps.map { app ->
            val totalTime = usageByPackage[app.packageName] ?: 0L
            InstalledApp(
                packageName = app.packageName,
                appName = app.appName,
                icon = app.icon,
                usageTimeMinutes = (totalTime / 60000).toInt(),
                isBlocked = app.packageName in alreadyBlocked
            )
        }
            .sortedWith(
                compareByDescending<InstalledApp> { it.usageTimeMinutes }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
                    .thenBy { it.packageName },
            )
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleBlock(app: InstalledApp) = viewModelScope.launch(appLoadDispatcher) {
        if (app.isBlocked) {
            repo.unblockApp(BlockedApp(packageName = app.packageName, appName = app.appName))
        } else {
            repo.blockApp(BlockedApp(packageName = app.packageName, appName = app.appName))
        }
        
        // Update local state immediately
        _allApps.update { current ->
            current.map {
                if (it.packageName == app.packageName) it.copy(isBlocked = !app.isBlocked)
                else it
            }
        }
    }

    private companion object {
        const val TAG = "AppSelectViewModel"
    }
}

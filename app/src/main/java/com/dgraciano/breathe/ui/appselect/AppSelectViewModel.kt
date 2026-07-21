package com.dgraciano.breathe.ui.appselect

import android.content.Context
import android.content.pm.ApplicationInfo
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
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
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
            val alreadyBlocked = repo.getAllBlockedPackageNames().toSet()
            
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || pm.getLaunchIntentForPackage(it.packageName) != null }
                .filter { it.packageName != context.packageName }
                .map { info ->
                    InstalledApp(
                        packageName = info.packageName,
                        appName = pm.getApplicationLabel(info).toString(),
                        icon = try { pm.getApplicationIcon(info) } catch (e: Exception) { null }
                    )
                }
                .filter { it.packageName !in alreadyBlocked }
                .sortedBy { it.appName }
            
            _allApps.value = installed
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun blockApp(app: InstalledApp) = viewModelScope.launch {
        repo.blockApp(BlockedApp(packageName = app.packageName, appName = app.appName))
        // Update local list to remove blocked app immediately
        _allApps.update { current -> current.filter { it.packageName != app.packageName } }
    }
}

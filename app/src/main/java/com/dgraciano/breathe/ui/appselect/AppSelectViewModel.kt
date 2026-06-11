package com.dgraciano.breathe.ui.appselect

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InstalledApp(val packageName: String, val appName: String)

@HiltViewModel
class AppSelectViewModel @Inject constructor(
    private val repo: AppRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps

    init { loadInstalledApps() }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val alreadyBlocked = repo.getAllBlockedPackageNames().toSet()
            _apps.value = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .filter { it.packageName !in alreadyBlocked }
                .filter { it.packageName != context.packageName }
                .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.appName }
        }
    }

    fun blockApp(app: InstalledApp) = viewModelScope.launch {
        repo.blockApp(BlockedApp(packageName = app.packageName, appName = app.appName))
    }
}

package com.dgraciano.breathe.ui.appselect

import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val usageTimeMinutes: Int? = null,
    val isBlocked: Boolean = false
)

@HiltViewModel
class AppSelectViewModel @Inject constructor(
    private val repo: AppRepository,
    private val installedAppsLoader: InstalledAppsLoader
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

    private val _savingPackages = MutableStateFlow<Set<String>>(emptySet())
    val savingPackages: StateFlow<Set<String>> = _savingPackages
    private val messages = Channel<String>(Channel.BUFFERED)
    val feedback = messages.receiveAsFlow()
    private var loadJob: Job? = null

    init { loadInstalledApps() }

    fun loadInstalledApps() {
        if (loadJob?.isActive == true || _savingPackages.value.isNotEmpty()) return
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val blocked = repo.getAllBlockedPackageNames().toSet()
                _allApps.value = installedAppsLoader.load().map {
                    it.copy(isBlocked = it.packageName in blocked)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not list installed apps", e)
                _errorMessage.value = "Couldn't load your apps. Tap Try again to reload."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleBlock(app: InstalledApp) {
        if (_isLoading.value || app.packageName in _savingPackages.value) return
        // Use current state, even if a click callback holds an older row instance.
        val current = _allApps.value.find { it.packageName == app.packageName } ?: return
        _savingPackages.update { it + current.packageName }
        viewModelScope.launch {
            try {
                val entity = BlockedApp(packageName = current.packageName, appName = current.appName)
                if (current.isBlocked) repo.unblockApp(entity) else repo.blockApp(entity)
                _allApps.update { list ->
                    list.map {
                        if (it.packageName == current.packageName) it.copy(isBlocked = !current.isBlocked)
                        else it
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not change monitored app", e)
                messages.send("Couldn't save the change for ${current.appName}. Tap the app to try again.")
            } finally {
                _savingPackages.update { it - current.packageName }
            }
        }
    }

    private companion object {
        const val TAG = "AppSelectViewModel"
    }
}

package com.dgraciano.breathe.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.db.InterventionEventDao
import com.dgraciano.breathe.data.repository.SettingsRepository
import com.dgraciano.breathe.service.BreatheAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val eventDao: InterventionEventDao
) : ViewModel() {

    val pauseSeconds: StateFlow<Int> = settings.pauseSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8)

    private val _isMonitoringActive = MutableStateFlow(false)
    val isMonitoringActive: StateFlow<Boolean> = _isMonitoringActive

    private val _historyCleared = MutableStateFlow(false)
    val historyCleared: StateFlow<Boolean> = _historyCleared

    init { refreshMonitoringState() }

    fun refreshMonitoringState() {
        _isMonitoringActive.value = BreatheAccessibilityService.isEnabled(context)
    }

    fun setPauseSeconds(seconds: Int) = viewModelScope.launch {
        settings.setPauseSeconds(seconds)
    }

    /** Deletes all recorded interventions. The app had no way to clear this data. */
    fun clearHistory() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            runCatching { eventDao.deleteOlderThan(System.currentTimeMillis()) }
        }
        _historyCleared.value = true
    }

    fun acknowledgeHistoryCleared() { _historyCleared.value = false }
}

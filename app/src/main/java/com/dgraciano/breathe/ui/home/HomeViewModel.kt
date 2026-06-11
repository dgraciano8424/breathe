package com.dgraciano.breathe.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.service.AppMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: AppRepository,
    private val statsRepo: StatsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val blockedApps = repo.getBlockedApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _todayAttempts = MutableStateFlow(0)
    val todayAttempts: StateFlow<Int> = _todayAttempts

    private val _todayDeclined = MutableStateFlow(0)
    val todayDeclined: StateFlow<Int> = _todayDeclined

    fun removeApp(app: BlockedApp) = viewModelScope.launch { repo.unblockApp(app) }

    fun startService() = AppMonitorService.start(context)

    fun refreshStats() {
        viewModelScope.launch {
            _todayAttempts.value = statsRepo.getTodayTotalAttempts()
            _todayDeclined.value = statsRepo.getTodayDeclined()
        }
    }
}

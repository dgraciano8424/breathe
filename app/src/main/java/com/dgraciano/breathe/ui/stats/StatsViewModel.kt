package com.dgraciano.breathe.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.AppStat
import com.dgraciano.breathe.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val todayAttempts: Int = 0,
    val todayDeclined: Int = 0,
    val weeklyAttempts: Int = 0,
    val weeklyDeclined: Int = 0,
    val topApps: List<AppStat> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepo: StatsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _state.value = StatsUiState(
                todayAttempts = statsRepo.getTodayTotalAttempts(),
                todayDeclined = statsRepo.getTodayDeclined(),
                weeklyAttempts = statsRepo.getWeeklyTotalAttempts(),
                weeklyDeclined = statsRepo.getWeeklyDeclined(),
                topApps = statsRepo.getTopAppsThisWeek(),
                isLoading = false
            )
        }
    }
}

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
    val focusStreak: Int = 0,
    val lifeWonBackActivity: String = "",
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
            val todayDeclined = statsRepo.getTodayDeclined()
            val streak = statsRepo.getFocusStreak()
            
            val savedMinutes = todayDeclined * 20
            val activity = when {
                savedMinutes >= 60 -> "read 30 pages of a physical book"
                savedMinutes >= 30 -> "take a long walk in the park"
                savedMinutes >= 15 -> "call a friend just to say hello"
                savedMinutes > 0 -> "practice 5 minutes of deep breathing"
                else -> "start your first mindful pause today"
            }

            _state.value = StatsUiState(
                todayAttempts = statsRepo.getTodayTotalAttempts(),
                todayDeclined = todayDeclined,
                weeklyAttempts = statsRepo.getWeeklyTotalAttempts(),
                weeklyDeclined = statsRepo.getWeeklyDeclined(),
                focusStreak = streak,
                lifeWonBackActivity = activity,
                topApps = statsRepo.getTopAppsThisWeek(),
                isLoading = false
            )
        }
    }
}

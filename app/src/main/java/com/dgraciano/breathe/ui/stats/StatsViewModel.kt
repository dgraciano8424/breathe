package com.dgraciano.breathe.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.AppStat
import com.dgraciano.breathe.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val todayMinutesSaved: Int = 0,
    val weeklyMinutesSaved: Int = 0,
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
    private var statsJob: Job? = null

    init {
        loadStats()
    }

    fun loadStats() {
        if (statsJob?.isActive == true) return
        statsJob = viewModelScope.launch {
            statsRepo.observeTodayAggregate().collect { today ->
                val streak = statsRepo.getFocusStreak()

                // Real, per-event minutes recorded at decline time — not an estimate.
                val activity = when {
                    today.minutesSaved >= 60 -> "read 30 pages of a physical book"
                    today.minutesSaved >= 30 -> "take a long walk in the park"
                    today.minutesSaved >= 15 -> "call a friend just to say hello"
                    today.minutesSaved > 0 -> "practice 5 minutes of deep breathing"
                    else -> ""
                }

                _state.value = StatsUiState(
                    todayAttempts = today.attempts,
                    todayDeclined = today.declined,
                    weeklyAttempts = statsRepo.getWeeklyTotalAttempts(),
                    weeklyDeclined = statsRepo.getWeeklyDeclined(),
                    focusStreak = streak,
                    todayMinutesSaved = today.minutesSaved,
                    weeklyMinutesSaved = statsRepo.getWeeklyMinutesSaved(),
                    lifeWonBackActivity = activity,
                    topApps = statsRepo.getTopAppsThisWeek(),
                    isLoading = false
                )
            }
        }
    }
}

package com.dgraciano.breathe.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.AppStat
import com.dgraciano.breathe.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepo: StatsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state

    private var loadJob: Job? = null

    fun loadStats() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val todayDeclined = statsRepo.getTodayDeclined()
                val streak = statsRepo.getFocusStreak()

                // Per-event estimates recorded at decline time, rather than multiplying a count.
                val savedMinutes = statsRepo.getTodayMinutesSaved()
                val activity = when {
                    savedMinutes >= 60 -> "read 30 pages of a physical book"
                    savedMinutes >= 30 -> "take a long walk in the park"
                    savedMinutes >= 15 -> "call a friend just to say hello"
                    savedMinutes >= 5 -> "practice 5 minutes of deep breathing"
                    savedMinutes > 0 -> "take a few slow breaths"
                    else -> ""
                }

                _state.value = StatsUiState(
                    todayAttempts = statsRepo.getTodayTotalAttempts(),
                    todayDeclined = todayDeclined,
                    weeklyAttempts = statsRepo.getWeeklyTotalAttempts(),
                    weeklyDeclined = statsRepo.getWeeklyDeclined(),
                    focusStreak = streak,
                    todayMinutesSaved = savedMinutes,
                    weeklyMinutesSaved = statsRepo.getWeeklyMinutesSaved(),
                    lifeWonBackActivity = activity,
                    topApps = statsRepo.getTopAppsThisWeek(),
                    isLoading = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Couldn't load your insights. Try again."
                )
            }
        }
    }
}

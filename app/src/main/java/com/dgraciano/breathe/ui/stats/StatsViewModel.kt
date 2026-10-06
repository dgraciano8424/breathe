package com.dgraciano.breathe.ui.stats

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.AppStat
import com.dgraciano.breathe.data.model.writeHistoryCsv
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.di.IoDispatcher
import com.dgraciano.breathe.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StatsUiState(
    val todayAttempts: Int = 0, val todayDeclined: Int = 0,
    val weeklyAttempts: Int = 0, val weeklyDeclined: Int = 0,
    val todayMinutesSaved: Int = 0, val weeklyMinutesSaved: Int = 0,
    val topApps: List<AppStat> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepo: StatsRepository,
    private val widgetRefresher: WidgetRefresher,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val mutableState = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = mutableState
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = mutableNotice
    private val mutableWorking = MutableStateFlow(false)
    val working: StateFlow<Boolean> = mutableWorking
    private var loadJob: Job? = null

    init { loadStats() }

    private suspend fun readStats() = StatsUiState(
        todayAttempts = statsRepo.getTodayTotalAttempts(), todayDeclined = statsRepo.getTodayDeclined(),
        weeklyAttempts = statsRepo.getWeeklyTotalAttempts(), weeklyDeclined = statsRepo.getWeeklyDeclined(),
        todayMinutesSaved = statsRepo.getTodayMinutesSaved(), weeklyMinutesSaved = statsRepo.getWeeklyMinutesSaved(),
        topApps = statsRepo.getTopAppsThisWeek(), isLoading = false
    )

    fun loadStats() {
        if (mutableWorking.value) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try { mutableState.value = readStats() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                mutableState.value = mutableState.value.copy(isLoading = false)
                mutableNotice.value = "Your history could not be loaded. Reopen this screen to try again."
            }
        }
    }

    fun exportHistory(destination: Uri) {
        if (mutableWorking.value) return
        mutableWorking.value = true
        mutableNotice.value = null
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    val history = statsRepo.getHistory()
                    val stream = context.contentResolver.openOutputStream(destination, "wt") ?: error("Destination unavailable")
                    stream.bufferedWriter(Charsets.UTF_8).use { writeHistoryCsv(history, it) }
                }
                mutableNotice.value = "History exported. Your history is still saved in Breathe."
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableNotice.value = "The export could not finish. Try another destination. An incomplete file may remain." }
            finally { mutableWorking.value = false }
        }
    }

    fun clearHistory() {
        if (mutableWorking.value) return
        mutableWorking.value = true
        mutableNotice.value = null
        loadJob?.cancel()
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { statsRepo.clearHistory() }
                mutableState.value = StatsUiState(isLoading = false)
                runCatching { widgetRefresher.refresh() }
                mutableNotice.value = "History cleared. Your monitored apps are unchanged."
                try { mutableState.value = readStats() }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { mutableNotice.value = "History cleared. Reopen this screen to refresh new choices." }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableNotice.value = "History could not be cleared. Try again." }
            finally { mutableWorking.value = false }
        }
    }
}

package com.dgraciano.breathe.ui.pause

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.repository.MentalHealthTip
import com.dgraciano.breathe.data.repository.MentalHealthTipsRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.service.SessionTimeHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PauseViewModel @Inject constructor(
    private val statsRepo: StatsRepository,
    private val tipsRepo: MentalHealthTipsRepository,
    private val sessionTimeHelper: SessionTimeHelper
) : ViewModel() {

    private val _attemptCount = MutableStateFlow(0)
    val attemptCount: StateFlow<Int> = _attemptCount

    private val _selectedReason = MutableStateFlow<String?>(null)
    val selectedReason: StateFlow<String?> = _selectedReason
    
    private val _tip = MutableStateFlow(tipsRepo.getRandomTip())
    val tip: StateFlow<MentalHealthTip> = _tip
    
    private val _alternativeActivity = MutableStateFlow(tipsRepo.getRandomActivity())
    val alternativeActivity: StateFlow<String> = _alternativeActivity

    var currentPackage: String = ""
        private set
    var currentAppName: String = ""
        private set

    /**
     * Exposed as state so a re-delivered intent (singleTask -> onNewIntent) updates the
     * rendered app name. Reading it from the Activity's onCreate local would keep showing
     * the app from the *first* launch.
     */
    private val _appName = MutableStateFlow("")
    val appName: StateFlow<String> = _appName

    fun init(packageName: String, appName: String) {
        currentPackage = packageName
        currentAppName = appName
        _appName.value = appName
        _selectedReason.value = null
        viewModelScope.launch {
            _attemptCount.value = statsRepo.getTodayAttemptCount(packageName) + 1
        }
    }

    /** Tapping the selected reason again clears it, so a mis-tap is recoverable. */
    fun selectReason(reason: String) {
        _selectedReason.value = if (_selectedReason.value == reason) null else reason
    }

    /**
     * Suspends until the event is persisted. Callers must await this before finishing the
     * screen — the previous fire-and-forget launch raced `onCleared()` cancelling
     * `viewModelScope`, so interventions were silently lost.
     */
    suspend fun recordDeclined() {
        val saved = withContext(Dispatchers.IO) {
            sessionTimeHelper.getAvgSessionMinutes(currentPackage)
        }
        statsRepo.recordEvent(
            InterventionEvent(
                packageName = currentPackage,
                appName = currentAppName,
                outcome = InterventionEvent.OUTCOME_DECLINED,
                // Left null when the user didn't pick one. Defaulting to HABIT
                // would fabricate self-reported data the user never gave.
                reason = _selectedReason.value,
                minutesSaved = saved
            )
        )
    }

    suspend fun recordOpened() {
        statsRepo.recordEvent(
            InterventionEvent(
                packageName = currentPackage,
                appName = currentAppName,
                outcome = InterventionEvent.OUTCOME_OPENED,
                reason = _selectedReason.value
            )
        )
    }
}

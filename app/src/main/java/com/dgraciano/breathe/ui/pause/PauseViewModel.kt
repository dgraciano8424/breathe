package com.dgraciano.breathe.ui.pause

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.model.Quote
import com.dgraciano.breathe.data.repository.MentalHealthTip
import com.dgraciano.breathe.data.repository.MentalHealthTipsRepository
import com.dgraciano.breathe.data.repository.QuoteRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.di.ApplicationScope
import com.dgraciano.breathe.service.SessionApprovalStore
import com.dgraciano.breathe.service.SessionTimeHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PauseViewModel @Inject constructor(
    private val quoteRepo: QuoteRepository,
    private val statsRepo: StatsRepository,
    private val tipsRepo: MentalHealthTipsRepository,
    private val sessionTimeHelper: SessionTimeHelper,
    private val sessionApprovalStore: SessionApprovalStore,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _quote = MutableStateFlow<Quote?>(null)
    val quote: StateFlow<Quote?> = _quote

    private val _attemptCount = MutableStateFlow(0)
    val attemptCount: StateFlow<Int> = _attemptCount

    private val _selectedReason = MutableStateFlow<String?>(null)
    val selectedReason: StateFlow<String?> = _selectedReason
    
    private val _tip = MutableStateFlow(tipsRepo.getRandomTip())
    val tip: StateFlow<MentalHealthTip> = _tip
    
    private val _alternativeActivity = MutableStateFlow(tipsRepo.getRandomActivity())
    val alternativeActivity: StateFlow<String> = _alternativeActivity

    var currentPackage: String = ""
    var currentAppName: String = ""

    fun init(packageName: String, appName: String) {
        currentPackage = packageName
        currentAppName = appName
        viewModelScope.launch {
            _quote.value = quoteRepo.getRandomQuote()
            _attemptCount.value = statsRepo.getTodayAttemptCount(packageName) + 1
        }
    }

    fun selectReason(reason: String) {
        _selectedReason.value = if (_selectedReason.value == reason) null else reason
    }

    /**
     * PauseActivity finishes the moment the user chooses, which clears this ViewModel
     * and cancels [viewModelScope]. Both writes therefore run on [appScope] so the
     * event still reaches Room. The state each one needs is read up front, since
     * `onNewIntent` can retarget this ViewModel while the write is in flight.
     */
    fun recordDeclined() {
        val packageName = currentPackage
        val appName = currentAppName
        val reason = _selectedReason.value
        appScope.launch {
            // Off the main thread: this scans up to 28 days of usage events.
            val saved = sessionTimeHelper.getAvgSessionMinutes(packageName)
            statsRepo.recordEvent(
                InterventionEvent(
                    packageName = packageName,
                    appName = appName,
                    outcome = InterventionEvent.OUTCOME_DECLINED,
                    // Left null when the user skipped the reason chips — don't invent one.
                    reason = reason,
                    minutesSaved = saved
                )
            )
        }
    }

    fun recordOpened() {
        // Approve synchronously so the session is granted even if the activity
        // finishes before the coroutine below completes.
        sessionApprovalStore.approve(currentPackage)
        val packageName = currentPackage
        val appName = currentAppName
        val reason = _selectedReason.value
        appScope.launch {
            statsRepo.recordEvent(
                InterventionEvent(
                    packageName = packageName,
                    appName = appName,
                    outcome = InterventionEvent.OUTCOME_OPENED,
                    reason = reason
                )
            )
        }
    }
}

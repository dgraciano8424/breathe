package com.dgraciano.breathe.ui.pause

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.model.Quote
import com.dgraciano.breathe.data.repository.MentalHealthTip
import com.dgraciano.breathe.data.repository.MentalHealthTipsRepository
import com.dgraciano.breathe.data.repository.QuoteRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PauseViewModel @Inject constructor(
    private val quoteRepo: QuoteRepository,
    private val statsRepo: StatsRepository,
    private val tipsRepo: MentalHealthTipsRepository
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
        _selectedReason.value = reason
    }

    fun recordDeclined() {
        viewModelScope.launch {
            statsRepo.recordEvent(
                InterventionEvent(
                    packageName = currentPackage,
                    appName = currentAppName,
                    outcome = InterventionEvent.OUTCOME_DECLINED,
                    reason = _selectedReason.value ?: InterventionEvent.REASON_HABIT
                )
            )
        }
    }

    fun recordOpened() {
        viewModelScope.launch {
            statsRepo.recordEvent(
                InterventionEvent(
                    packageName = currentPackage,
                    appName = currentAppName,
                    outcome = InterventionEvent.OUTCOME_OPENED,
                    reason = _selectedReason.value ?: InterventionEvent.REASON_HABIT
                )
            )
        }
    }
}

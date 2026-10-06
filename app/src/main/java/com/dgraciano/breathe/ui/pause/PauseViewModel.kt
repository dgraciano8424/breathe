package com.dgraciano.breathe.ui.pause

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.model.PendingChoice
import com.dgraciano.breathe.data.model.pauseReasonKeys
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.ChoiceRecorder
import com.dgraciano.breathe.data.repository.MentalHealthTipsRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.di.ApplicationScope
import com.dgraciano.breathe.domain.PausePolicy
import com.dgraciano.breathe.service.PauseClock
import com.dgraciano.breathe.service.SessionApprovalStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PauseViewModel @Inject constructor(
    private val statsRepo: StatsRepository,
    private val appRepo: AppRepository,
    tipsRepo: MentalHealthTipsRepository,
    private val sessionApprovalStore: SessionApprovalStore,
    private val choiceRecorder: ChoiceRecorder,
    private val clock: PauseClock,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {
    private val _attemptCount = MutableStateFlow(0)
    val attemptCount = _attemptCount.asStateFlow()
    private val _selectedReason = MutableStateFlow<String?>(null)
    val selectedReason = _selectedReason.asStateFlow()
    private val _pauseSeconds = MutableStateFlow(BlockedApp.DEFAULT_PAUSE_SECONDS)
    val pauseSeconds = _pauseSeconds.asStateFlow()
    val tip = MutableStateFlow(tipsRepo.getRandomTip()).asStateFlow()
    val alternativeActivity = MutableStateFlow(tipsRepo.getRandomActivity()).asStateFlow()
    private val _sessionId = MutableStateFlow(0)
    val sessionId = _sessionId.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _secondsRemaining = MutableStateFlow(BlockedApp.DEFAULT_PAUSE_SECONDS)
    val secondsRemaining = _secondsRemaining.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()
    var currentPackage: String = ""
        private set
    var currentAppName: String = ""
        private set
    private var initJob: Job? = null
    private var countdownJob: Job? = null
    private var generation = 0
    private var choiceId = ""
    private var policy = PausePolicy(clock::now)

    fun init(packageName: String, appName: String) {
        initJob?.cancel()
        countdownJob?.cancel()
        val session = ++generation
        choiceId = UUID.randomUUID().toString()
        policy = PausePolicy(clock::now)
        _sessionId.value = session
        _ready.value = false
        _saving.value = false
        _saveError.value = null
        _selectedReason.value = null
        _attemptCount.value = 1
        currentPackage = packageName
        currentAppName = appName
        _pauseSeconds.value = BlockedApp.DEFAULT_PAUSE_SECONDS
        _secondsRemaining.value = BlockedApp.DEFAULT_PAUSE_SECONDS
        initJob = viewModelScope.launch {
            try {
                val duration = appRepo.getPauseSeconds(packageName)
                if (session == generation) {
                    _pauseSeconds.value = duration
                    _secondsRemaining.value = duration
                }
                val attempts = statsRepo.getTodayAttemptCount(packageName) + 1
                if (session == generation) _attemptCount.value = attempts
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("PauseViewModel", "Could not load pause settings", error)
            } finally {
                if (session == generation) _ready.value = true
            }
        }
    }

    /** Called when the host renders the ready pause, rather than before attachment. */
    fun startCountdown() {
        if (!_ready.value || countdownJob?.isActive == true) return
        val session = generation
        val currentPolicy = policy
        currentPolicy.start(_pauseSeconds.value)
        countdownJob = viewModelScope.launch {
            do {
                val left = currentPolicy.remainingSeconds()
                if (session != generation) return@launch
                _secondsRemaining.value = left
                if (left == 0) break
                delay(100)
            } while (isActive)
        }
    }

    fun selectReason(reason: String) {
        if (_saving.value || reason !in pauseReasonKeys) return
        _selectedReason.value = if (_selectedReason.value == reason) null else reason
    }

    fun recordDeclined(onSaved: () -> Unit = {}) = choose(false, onSaved)
    fun recordOpened(onSaved: () -> Unit = {}) = choose(true, onSaved)

    private fun choose(proceed: Boolean, onSaved: () -> Unit) {
        if (currentPackage.isBlank() || !viewModelScope.isActive || !policy.beginChoice(proceed)) return
        val session = generation
        val currentPolicy = policy
        val choice = PendingChoice(
            choiceId = choiceId, packageName = currentPackage, appName = currentAppName,
            timestamp = System.currentTimeMillis(),
            outcome = if (proceed) InterventionEvent.OUTCOME_OPENED else InterventionEvent.OUTCOME_DECLINED,
            reason = _selectedReason.value
        )
        _saving.value = true
        _saveError.value = null
        appScope.launch {
            try {
                choiceRecorder.enqueue(choice)
                withContext(Dispatchers.Main.immediate) {
                    if (session == generation && viewModelScope.isActive) {
                        if (proceed) sessionApprovalStore.approve(choice.packageName)
                        onSaved()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    if (session == generation && viewModelScope.isActive) {
                        currentPolicy.retryChoice()
                        _saving.value = false
                        _saveError.value = "Could not save your choice. Please try again."
                    }
                }
            }
        }
    }
}

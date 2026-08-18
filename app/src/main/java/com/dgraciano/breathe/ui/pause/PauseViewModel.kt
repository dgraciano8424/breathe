package com.dgraciano.breathe.ui.pause

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.MentalHealthTip
import com.dgraciano.breathe.data.repository.MentalHealthTipsRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.di.ApplicationScope
import com.dgraciano.breathe.service.InterventionCoordinator
import com.dgraciano.breathe.service.SessionApprovalStore
import com.dgraciano.breathe.service.SessionTimeHelper
import com.dgraciano.breathe.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PauseViewModel private constructor(
    private val statsRepo: StatsRepository,
    private val appRepo: AppRepository,
    private val tipsRepo: MentalHealthTipsRepository,
    private val sessionTimeHelper: SessionTimeHelper,
    private val sessionApprovalStore: SessionApprovalStore,
    private val widgetRefresher: WidgetRefresher,
    @ApplicationScope private val appScope: CoroutineScope,
    private val coordinator: InterventionCoordinator?,
    @Suppress("UNUSED_PARAMETER") constructorMarker: Unit,
) : ViewModel() {

    @Inject
    constructor(
        statsRepo: StatsRepository,
        appRepo: AppRepository,
        tipsRepo: MentalHealthTipsRepository,
        sessionTimeHelper: SessionTimeHelper,
        sessionApprovalStore: SessionApprovalStore,
        widgetRefresher: WidgetRefresher,
        @ApplicationScope appScope: CoroutineScope,
        coordinator: InterventionCoordinator,
    ) : this(
        statsRepo,
        appRepo,
        tipsRepo,
        sessionTimeHelper,
        sessionApprovalStore,
        widgetRefresher,
        appScope,
        coordinator,
        Unit,
    )

    /** Compatibility constructor for the existing JVM tests of the legacy persistence seam. */
    constructor(
        statsRepo: StatsRepository,
        appRepo: AppRepository,
        tipsRepo: MentalHealthTipsRepository,
        sessionTimeHelper: SessionTimeHelper,
        sessionApprovalStore: SessionApprovalStore,
        widgetRefresher: WidgetRefresher,
        @ApplicationScope appScope: CoroutineScope,
    ) : this(
        statsRepo,
        appRepo,
        tipsRepo,
        sessionTimeHelper,
        sessionApprovalStore,
        widgetRefresher,
        appScope,
        null,
        Unit,
    )

    private val _attemptCount = MutableStateFlow(0)
    val attemptCount: StateFlow<Int> = _attemptCount

    private val _selectedReason = MutableStateFlow<String?>(null)
    val selectedReason: StateFlow<String?> = _selectedReason

    private val _pauseSeconds = MutableStateFlow(BlockedApp.DEFAULT_PAUSE_SECONDS)
    val pauseSeconds: StateFlow<Int> = _pauseSeconds
    
    private val _tip = MutableStateFlow(tipsRepo.getRandomTip())
    val tip: StateFlow<MentalHealthTip> = _tip
    
    private val _alternativeActivity = MutableStateFlow(tipsRepo.getRandomActivity())
    val alternativeActivity: StateFlow<String> = _alternativeActivity

    var currentPackage: String = ""
    var currentAppName: String = ""
    private var currentRequestId: String = ""

    fun init(packageName: String, appName: String) {
        init(requestId = "", packageName = packageName, appName = appName)
    }

    fun init(requestId: String, packageName: String, appName: String) {
        currentRequestId = requestId
        currentPackage = packageName
        currentAppName = appName
        // Reset so a retargeted pause never inherits the previous app's duration.
        _pauseSeconds.value = BlockedApp.DEFAULT_PAUSE_SECONDS
        viewModelScope.launch {
            _pauseSeconds.value = appRepo.getPauseSeconds(packageName)
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
        coordinator?.let {
            it.recordDeclined(currentRequestId, appName, reason)
            return
        }
        appScope.launch {
            // Off the main thread: this scans a week of usage events.
            val saved = sessionTimeHelper.getAvgSessionMinutes(packageName)
            recordOrLog(
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
        coordinator?.let {
            it.recordOpened(currentRequestId, currentAppName, _selectedReason.value)
            return
        }
        // Approve synchronously so the session is granted even if the activity
        // finishes before the coroutine below completes.
        sessionApprovalStore.approve(currentPackage)
        val packageName = currentPackage
        val appName = currentAppName
        val reason = _selectedReason.value
        appScope.launch {
            recordOrLog(
                InterventionEvent(
                    packageName = packageName,
                    appName = appName,
                    outcome = InterventionEvent.OUTCOME_OPENED,
                    reason = reason
                )
            )
        }
    }

    /**
     * The screen is already gone by the time these run, so there is nobody to show an
     * error to and nothing useful to retry into. Failing loudly in the log at least
     * makes a silently missing statistic diagnosable instead of invisible.
     */
    private suspend fun recordOrLog(event: InterventionEvent) {
        runCatching { statsRepo.recordEvent(event) }
            .onSuccess { widgetRefresher.refresh() }
            .onFailure {
                Log.e(TAG, "Failed to record ${event.outcome} for ${event.packageName}", it)
            }
    }

    private companion object {
        const val TAG = "PauseViewModel"
    }
}

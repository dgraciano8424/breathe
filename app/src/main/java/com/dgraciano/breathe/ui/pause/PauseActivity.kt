package com.dgraciano.breathe.ui.pause

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.dgraciano.breathe.service.InterventionCoordinator
import com.dgraciano.breathe.ui.theme.BreatheTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Process-scoped truth for fallback requests, independent of any Activity instance. */
@Singleton
class PauseActivityRequestRegistry @Inject constructor() {
    private val states = linkedMapOf<String, RequestState>()
    private val mutableClosedRequestIds = MutableStateFlow<Set<String>>(emptySet())
    val closedRequestIds: StateFlow<Set<String>> = mutableClosedRequestIds.asStateFlow()

    @Synchronized
    fun open(requestId: String) {
        if (requestId.isEmpty()) return
        states.putIfAbsent(requestId, RequestState.Open)
    }

    @Synchronized
    fun close(requestId: String) {
        if (requestId.isEmpty()) return
        states[requestId] = RequestState.Closed
        mutableClosedRequestIds.value = mutableClosedRequestIds.value + requestId
        trimClosedRequests()
    }

    @Synchronized
    fun isOpen(requestId: String): Boolean = when (states[requestId]) {
        RequestState.Open,
        RequestState.Acknowledged,
        -> true
        RequestState.Closed,
        null,
        -> false
    }

    @Synchronized
    fun claimAcknowledgement(requestId: String): Boolean {
        if (states[requestId] != RequestState.Open) return false
        states[requestId] = RequestState.Acknowledged
        return true
    }

    @Synchronized
    fun isAcknowledged(requestId: String): Boolean =
        states[requestId] == RequestState.Acknowledged

    private fun trimClosedRequests() {
        val closed = states.filterValues { it == RequestState.Closed }.keys
        if (closed.size <= MAX_CLOSED_REQUESTS) return
        val expired = closed.take(closed.size - MAX_CLOSED_REQUESTS)
        expired.forEach(states::remove)
        mutableClosedRequestIds.value = mutableClosedRequestIds.value - expired.toSet()
    }

    private enum class RequestState { Open, Acknowledged, Closed }

    private companion object {
        const val MAX_CLOSED_REQUESTS = 64
    }
}

@AndroidEntryPoint
class PauseActivity : ComponentActivity() {

    private val viewModel: PauseViewModel by viewModels()
    @Inject lateinit var coordinator: InterventionCoordinator
    @Inject lateinit var activityRequests: PauseActivityRequestRegistry

    private var target by mutableStateOf(PauseTarget.EMPTY)
    private var resumed = false
    private var contentReadyRequestId: String? = null
    private var terminalActionSubmitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenPresentation()
        installBackHandling()
        if (!retarget(intent)) return
        lifecycleScope.launch {
            activityRequests.closedRequestIds.collect { closed ->
                if (target.requestId in closed) finish()
            }
        }

        setContent {
            val current = target
            BreatheTheme {
                val attemptCount by viewModel.attemptCount.collectAsState()
                val selectedReason by viewModel.selectedReason.collectAsState()
                val tip by viewModel.tip.collectAsState()
                val activity by viewModel.alternativeActivity.collectAsState()
                val pauseSeconds by viewModel.pauseSeconds.collectAsState()

                PauseScreen(
                    appName = current.appName,
                    attemptCount = attemptCount,
                    tip = tip,
                    alternativeActivity = activity,
                    selectedReason = selectedReason,
                    pauseSeconds = pauseSeconds,
                    onReasonSelected = viewModel::selectReason,
                    onYes = {
                        terminalActionSubmitted = true
                        viewModel.recordOpened()
                        finish()
                    },
                    onNo = {
                        terminalActionSubmitted = true
                        viewModel.recordDeclined()
                        startActivity(
                            Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        finish()
                    },
                )
            }

            LaunchedEffect(current.requestId) {
                if (current.requestId.isNotEmpty()) {
                    contentReadyRequestId = current.requestId
                    acknowledgeIfReady()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!activityRequests.isOpen(target.requestId)) {
            finish()
            return
        }
        acknowledgeIfReady()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations &&
            !terminalActionSubmitted &&
            activityRequests.isOpen(target.requestId)
        ) {
            coordinator.onPresentationLost(target.requestId, null)
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        retarget(intent)
    }

    private fun retarget(intent: Intent): Boolean {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        target = PauseTarget(requestId, packageName, appName)
        contentReadyRequestId = null
        terminalActionSubmitted = false
        if (!activityRequests.isOpen(requestId)) {
            finish()
            return false
        }
        viewModel.init(requestId, packageName, appName)
        return true
    }

    private fun acknowledgeIfReady() {
        val requestId = target.requestId
        if (!resumed || requestId.isEmpty() || contentReadyRequestId != requestId) return
        if (!activityRequests.claimAcknowledgement(requestId)) return
        coordinator.acknowledgeActivityContentReady(requestId)
    }

    private fun installBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                coordinator.dismissWithBack(target.requestId)
                finish()
            }
        })
    }

    private fun configureLockScreenPresentation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private data class PauseTarget(
        val requestId: String,
        val packageName: String,
        val appName: String,
    ) {
        companion object {
            val EMPTY = PauseTarget("", "", "")
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_REQUEST_ID = "extra_request_id"

        fun newIntent(context: Context, packageName: String, requestId: String = ""): Intent {
            val appName = runCatching {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(packageName)

            return Intent(context, PauseActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_REQUEST_ID, requestId)
            }
        }
    }
}

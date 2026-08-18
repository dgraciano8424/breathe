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
import com.dgraciano.breathe.service.InterventionCoordinator
import com.dgraciano.breathe.ui.theme.BreatheTheme
import dagger.hilt.android.AndroidEntryPoint
import java.lang.ref.WeakReference
import javax.inject.Inject

@AndroidEntryPoint
class PauseActivity : ComponentActivity() {

    private val viewModel: PauseViewModel by viewModels()
    @Inject lateinit var coordinator: InterventionCoordinator

    private var target by mutableStateOf(PauseTarget.EMPTY)
    private var resumed = false
    private var contentReadyRequestId: String? = null
    private var acknowledgedRequestId: String? = null
    private var terminalActionSubmitted = false

    /** The count is state, not a test callback: it enforces one acknowledgement per request. */
    internal var acknowledgementCount: Int = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenPresentation()
        installBackHandling()
        retarget(intent)

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
        activeActivity = WeakReference(this)
        acknowledgeIfReady()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onDestroy() {
        if (activeActivity?.get() === this) activeActivity = null
        if (!isChangingConfigurations && !terminalActionSubmitted && target.requestId.isNotEmpty()) {
            coordinator.onPresentationLost(target.requestId, null)
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        retarget(intent)
    }

    private fun retarget(intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        target = PauseTarget(requestId, packageName, appName)
        contentReadyRequestId = null
        acknowledgedRequestId = null
        terminalActionSubmitted = false
        viewModel.init(requestId, packageName, appName)
    }

    private fun acknowledgeIfReady() {
        val requestId = target.requestId
        if (!resumed || requestId.isEmpty() || contentReadyRequestId != requestId) return
        if (acknowledgedRequestId == requestId) return
        acknowledgedRequestId = requestId
        acknowledgementCount += 1
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

        private var activeActivity: WeakReference<PauseActivity>? = null

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

        internal fun finishRequest(requestId: String) {
            activeActivity?.get()
                ?.takeIf { it.target.requestId == requestId }
                ?.finish()
        }
    }
}

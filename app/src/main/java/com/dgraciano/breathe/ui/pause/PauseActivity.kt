package com.dgraciano.breathe.ui.pause

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dgraciano.breathe.ui.theme.BreatheTheme
import com.dgraciano.breathe.service.SnoozeStore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PauseActivity : ComponentActivity() {

    @Inject lateinit var snoozeStore: SnoozeStore

    private val viewModel: PauseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (snoozeStore.isSnoozed()) { finish(); return }
        lifecycleScope.launch {
            snoozeStore.deadline.collect { until -> if (until > 0) finish() }
        }

        // Show over the lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: blockedPackage

        viewModel.init(blockedPackage, appName)
        onBackPressedDispatcher.addCallback(this) { declineAndFinish() }

        setContent {
            BreatheTheme {
                val attemptCount by viewModel.attemptCount.collectAsState()
                val selectedReason by viewModel.selectedReason.collectAsState()
                val tip by viewModel.tip.collectAsState()
                val activity by viewModel.alternativeActivity.collectAsState()
                val pauseSeconds by viewModel.pauseSeconds.collectAsState()
                val sessionId by viewModel.sessionId.collectAsState()
                val ready by viewModel.ready.collectAsState()

                PauseScreen(
                    appName = viewModel.currentAppName,
                    attemptCount = attemptCount,
                    tip = tip,
                    alternativeActivity = activity,
                    selectedReason = selectedReason,
                    pauseSeconds = pauseSeconds,
                    sessionId = sessionId,
                    ready = ready,
                    onReasonSelected = viewModel::selectReason,
                    onYes = {
                        viewModel.recordOpened()
                        finish()
                    },
                    onNo = ::declineAndFinish
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (snoozeStore.isSnoozed()) { finish(); return }
        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: blockedPackage
        viewModel.init(blockedPackage, appName)
    }

    private fun declineAndFinish() {
        viewModel.recordDeclined()
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_APP_NAME = "extra_app_name"

        fun newIntent(context: Context, packageName: String): Intent {
            val appName = runCatching {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(packageName)

            return Intent(context, PauseActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_APP_NAME, appName)
            }
        }
    }
}

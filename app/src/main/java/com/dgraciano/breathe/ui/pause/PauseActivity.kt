package com.dgraciano.breathe.ui.pause

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.dgraciano.breathe.ui.theme.BreatheTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PauseActivity : ComponentActivity() {

    private val viewModel: PauseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        // The pause screen names the app the user is being nudged about. Keep it out of
        // recents thumbnails and screenshots.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: blockedPackage

        viewModel.init(blockedPackage, appName)

        setContent {
            BreatheTheme {
                val attemptCount by viewModel.attemptCount.collectAsState()
                val selectedReason by viewModel.selectedReason.collectAsState()
                val tip by viewModel.tip.collectAsState()
                val activity by viewModel.alternativeActivity.collectAsState()
                // From the ViewModel, so a re-delivered intent updates the name.
                val currentAppName by viewModel.appName.collectAsState()

                PauseScreen(
                    appName = currentAppName,
                    attemptCount = attemptCount,
                    tip = tip,
                    alternativeActivity = activity,
                    selectedReason = selectedReason,
                    onReasonSelected = viewModel::selectReason,
                    onYes = { dismissAfterRecording { viewModel.recordOpened() } },
                    onNo = {
                        dismissAfterRecording {
                            viewModel.recordDeclined()
                            startActivity(
                                Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_HOME)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }
        }
    }

    /**
     * Persists the intervention before tearing the screen down. Finishing first would
     * cancel the write.
     */
    private fun dismissAfterRecording(record: suspend () -> Unit) {
        lifecycleScope.launch {
            runCatching { record() }
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: blockedPackage
        viewModel.init(blockedPackage, appName)
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

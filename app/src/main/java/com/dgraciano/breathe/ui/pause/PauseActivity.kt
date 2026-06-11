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
import com.dgraciano.breathe.ui.theme.BreatheTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PauseActivity : ComponentActivity() {

    private val viewModel: PauseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lock screen — setShowWhenLocked replaces the deprecated manifest flag
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

        setContent {
            BreatheTheme {
                val quote by viewModel.quote.collectAsState()
                val attemptCount by viewModel.attemptCount.collectAsState()
                val selectedReason by viewModel.selectedReason.collectAsState()

                PauseScreen(
                    appName = appName,
                    attemptCount = attemptCount,
                    quote = quote,
                    selectedReason = selectedReason,
                    onReasonSelected = viewModel::selectReason,
                    onYes = {
                        viewModel.recordOpened()
                        packageManager.getLaunchIntentForPackage(blockedPackage)
                            ?.let { startActivity(it) }
                        finish()
                    },
                    onNo = {
                        viewModel.recordDeclined()
                        startActivity(
                            Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        finish()
                    }
                )
            }
        }
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

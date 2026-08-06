package com.dgraciano.breathe.ui.pause

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dgraciano.breathe.data.repository.MentalHealthTipsRepository
import com.dgraciano.breathe.data.repository.QuoteRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.di.ApplicationScope
import com.dgraciano.breathe.service.SessionApprovalStore
import com.dgraciano.breathe.service.SessionTimeHelper
import com.dgraciano.breathe.ui.theme.BreatheTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PauseOverlay"

/** Mirrors what PauseActivity renders, so both entry points show the same screen. */
@Composable
private fun PauseOverlayContent(
    appName: String,
    viewModel: PauseViewModel,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    val quote by viewModel.quote.collectAsState()
    val attemptCount by viewModel.attemptCount.collectAsState()
    val selectedReason by viewModel.selectedReason.collectAsState()
    val tip by viewModel.tip.collectAsState()
    val activity by viewModel.alternativeActivity.collectAsState()

    PauseScreen(
        appName = appName,
        attemptCount = attemptCount,
        quote = quote,
        tip = tip,
        alternativeActivity = activity,
        selectedReason = selectedReason,
        onReasonSelected = viewModel::selectReason,
        onYes = onYes,
        onNo = onNo
    )
}

/**
 * Shows the mindful-pause screen as a `TYPE_APPLICATION_OVERLAY` window rather than an
 * Activity.
 *
 * Starting an Activity from a background service is unreliable on Android 10+: a
 * foreground service is not a general exemption from background-activity-start
 * restrictions, so the pause screen could simply never appear. Drawing over the blocked
 * app instead sidesteps that entirely, and costs no new permission — the app already
 * requires SYSTEM_ALERT_WINDOW and the monitor already gated on it.
 *
 * A pleasant side effect: the blocked app never leaves the foreground, so choosing
 * "yes" just dismisses the overlay instead of relaunching anything.
 */
@Singleton
class PauseOverlayHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quoteRepo: QuoteRepository,
    private val statsRepo: StatsRepository,
    private val tipsRepo: MentalHealthTipsRepository,
    private val sessionTimeHelper: SessionTimeHelper,
    private val sessionApprovalStore: SessionApprovalStore,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { context.getSystemService(WindowManager::class.java) }

    private var root: FrameLayout? = null
    private var owners: OverlayOwners? = null

    /**
     * Read by the monitor loop from its polling thread. The blocked app stays in the
     * foreground behind the overlay, so without this the loop would immediately decide
     * it needs to intervene again.
     */
    @Volatile
    var isShowing: Boolean = false
        private set

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    /** Safe to call from any thread; window work is posted to the main looper. */
    fun show(packageName: String, appName: String) {
        isShowing = true
        mainHandler.post { showInternal(packageName, appName) }
    }

    fun hide() {
        isShowing = false
        mainHandler.post { hideInternal() }
    }

    @SuppressLint("InflateParams")
    private fun showInternal(packageName: String, appName: String) {
        if (root != null) return

        val overlayOwners = OverlayOwners().apply { create() }
        val viewModel = ViewModelProvider(
            overlayOwners.viewModelStore,
            viewModelFactory {
                initializer {
                    PauseViewModel(
                        quoteRepo, statsRepo, tipsRepo,
                        sessionTimeHelper, sessionApprovalStore, appScope
                    )
                }
            }
        )[PauseViewModel::class.java]
        viewModel.init(packageName, appName)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(overlayOwners)
            setViewTreeViewModelStoreOwner(overlayOwners)
            setViewTreeSavedStateRegistryOwner(overlayOwners)
            setContent {
                BreatheTheme {
                    PauseOverlayContent(
                        appName = appName,
                        viewModel = viewModel,
                        onYes = {
                            viewModel.recordOpened()
                            // The blocked app is still in the foreground behind us.
                            hide()
                        },
                        onNo = {
                            viewModel.recordDeclined()
                            hide()
                            goHome()
                        }
                    )
                }
            }
        }

        // A plain ComposeView cannot intercept the back key, so it is wrapped in a
        // container that can. Back dismisses without recording, matching what the
        // Activity did; the monitor re-intervenes after its debounce.
        val container = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    hide()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            addView(composeView)
            isFocusableInTouchMode = true
            requestFocus()
        }

        try {
            windowManager.addView(container, layoutParams())
        } catch (e: WindowManager.BadTokenException) {
            // Overlay permission can be revoked between the check and the add.
            Log.w(TAG, "Overlay rejected; falling back to the pause activity", e)
            overlayOwners.destroy()
            isShowing = false
            launchPauseActivity(packageName)
            return
        }

        root = container
        owners = overlayOwners
    }

    private fun hideInternal() {
        val container = root ?: return
        runCatching { windowManager.removeView(container) }
            .onFailure { Log.w(TAG, "Overlay already detached", it) }
        owners?.destroy()
        root = null
        owners = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Focusable on purpose: the window has to receive the back key. Touches
        // outside it are still blocked, which is the point of the intervention.
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    )

    private fun goHome() {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Last resort when the overlay window is refused. */
    private fun launchPauseActivity(packageName: String) {
        context.startActivity(
            PauseActivity.newIntent(context, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
    }

    /**
     * A ComposeView expects to inherit these from an Activity. An overlay window has no
     * Activity behind it, so the host supplies them and tears them down with the window.
     */
    private class OverlayOwners : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun create() {
            // Must restore before the registry is moved past CREATED.
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}

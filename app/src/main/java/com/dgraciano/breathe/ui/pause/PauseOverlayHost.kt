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
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.MentalHealthTipsRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.data.repository.PausePreferences
import com.dgraciano.breathe.di.ApplicationScope
import com.dgraciano.breathe.service.SessionApprovalStore
import com.dgraciano.breathe.service.SessionTimeHelper
import com.dgraciano.breathe.service.MonitoringStatus
import com.dgraciano.breathe.ui.theme.BreatheTheme
import com.dgraciano.breathe.widget.WidgetRefresher
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
    preferences: PausePreferences,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    val attemptCount by viewModel.attemptCount.collectAsState()
    val selectedReason by viewModel.selectedReason.collectAsState()
    val reminder by preferences.reminder.collectAsState()
    val tip by viewModel.tip.collectAsState()
    val activity by viewModel.alternativeActivity.collectAsState()
    val pauseSeconds by viewModel.pauseSeconds.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()
    val ready by viewModel.ready.collectAsState()

    PauseScreen(
        appName = appName,
        attemptCount = attemptCount,
        tip = tip,
        alternativeActivity = activity,
        selectedReason = selectedReason,
        personalReminder = reminder,
        pauseSeconds = pauseSeconds,
        sessionId = sessionId,
        ready = ready,
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
    private val statsRepo: StatsRepository,
    private val appRepo: AppRepository,
    private val tipsRepo: MentalHealthTipsRepository,
    private val sessionTimeHelper: SessionTimeHelper,
    private val sessionApprovalStore: SessionApprovalStore,
    private val widgetRefresher: WidgetRefresher,
    private val monitoringStatus: MonitoringStatus,
    private val pausePreferences: PausePreferences,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { context.getSystemService(WindowManager::class.java) }

    private var root: FrameLayout? = null
    private var owners: OverlayOwners? = null

    /**
     * Read from the accessibility service's event callback. The overlay draws over the
     * blocked app without displacing it, so that app keeps raising window events while
     * the pause is up; without this the service would immediately decide to intervene
     * again.
     */
    @Volatile
    var isShowing: Boolean = false
        private set
    @Volatile
    var activePackage: String? = null
        private set

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    /** Safe to call from any thread; window work is posted to the main looper. */
    fun show(packageName: String, appName: String) {
        activePackage = packageName
        isShowing = true
        mainHandler.post { showInternal(packageName, appName) }
    }

    fun hide() {
        activePackage = null
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
                        statsRepo = statsRepo,
                        appRepo = appRepo,
                        tipsRepo = tipsRepo,
                        sessionTimeHelper = sessionTimeHelper,
                        sessionApprovalStore = sessionApprovalStore,
                        widgetRefresher = widgetRefresher,
                        appScope = appScope
                    )
                }
            }
        )[PauseViewModel::class.java]
        viewModel.init(packageName, appName)

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(overlayOwners)
            setViewTreeViewModelStoreOwner(overlayOwners)
            setViewTreeSavedStateRegistryOwner(overlayOwners)
            setContent {
                BreatheTheme {
                    PauseOverlayContent(
                        appName = appName,
                        viewModel = viewModel,
                        preferences = pausePreferences,
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
        // container that can. Back means the same choice as the Go back button;
        // merely hiding the overlay would expose the app without approval.
        val container = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    viewModel.recordDeclined()
                    hide()
                    goHome()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            // Compose installs the window recomposer on the window root, not only
            // the ComposeView child. The root must expose these owners too.
            setViewTreeLifecycleOwner(overlayOwners)
            setViewTreeViewModelStoreOwner(overlayOwners)
            setViewTreeSavedStateRegistryOwner(overlayOwners)
            addView(composeView)
            isFocusableInTouchMode = true
            requestFocus()
        }

        try {
            windowManager.addView(container, layoutParams())
        } catch (e: RuntimeException) {
            // Overlay permission can be revoked between the check and the add.
            Log.w(TAG, "Overlay rejected; falling back to the pause activity", e)
            overlayOwners.destroy()
            isShowing = false
            activePackage = null
            monitoringStatus.failed()
            runCatching { launchPauseActivity(packageName) }
                .onFailure { Log.w(TAG, "Pause activity also unavailable", it) }
            return
        }

        root = container
        owners = overlayOwners
        monitoringStatus.pauseShown()
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

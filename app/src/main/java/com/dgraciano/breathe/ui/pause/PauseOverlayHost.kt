package com.dgraciano.breathe.ui.pause

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
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
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.MentalHealthTipsRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.di.ApplicationScope
import com.dgraciano.breathe.service.InterventionCoordinator
import com.dgraciano.breathe.service.PausePresentation
import com.dgraciano.breathe.service.PausePresentationCallbacks
import com.dgraciano.breathe.service.PauseRequest
import com.dgraciano.breathe.service.SessionApprovalStore
import com.dgraciano.breathe.service.SessionTimeHelper
import com.dgraciano.breathe.ui.theme.BreatheTheme
import com.dgraciano.breathe.widget.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private const val TAG = "PauseOverlay"

@Composable
private fun PauseOverlayContent(
    appName: String,
    viewModel: PauseViewModel,
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    val attemptCount by viewModel.attemptCount.collectAsState()
    val selectedReason by viewModel.selectedReason.collectAsState()
    val tip by viewModel.tip.collectAsState()
    val activity by viewModel.alternativeActivity.collectAsState()
    val pauseSeconds by viewModel.pauseSeconds.collectAsState()

    PauseScreen(
        appName = appName,
        attemptCount = attemptCount,
        tip = tip,
        alternativeActivity = activity,
        selectedReason = selectedReason,
        pauseSeconds = pauseSeconds,
        onReasonSelected = viewModel::selectReason,
        onYes = onYes,
        onNo = onNo,
    )
}

/** Android overlay implementation of the coordinator-owned pause presentation protocol. */
@Singleton
class PauseOverlayHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsRepo: StatsRepository,
    private val appRepo: AppRepository,
    private val tipsRepo: MentalHealthTipsRepository,
    private val sessionTimeHelper: SessionTimeHelper,
    private val sessionApprovalStore: SessionApprovalStore,
    private val widgetRefresher: WidgetRefresher,
    @ApplicationScope private val appScope: CoroutineScope,
    // Provider breaks the coordinator -> presentation -> coordinator construction cycle while
    // still resolving the exact application-scoped coordinator for decision and Back callbacks.
    private val coordinatorProvider: Provider<InterventionCoordinator>,
    private val activityRequests: PauseActivityRequestRegistry,
) : PausePresentation {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { context.getSystemService(WindowManager::class.java) }

    private var root: FrameLayout? = null
    private var owners: OverlayOwners? = null
    private var active: ActivePresentation? = null

    @Volatile
    var isShowing: Boolean = false
        private set

    override fun show(request: PauseRequest, callbacks: PausePresentationCallbacks) {
        mainHandler.post { showInternal(request, callbacks) }
    }

    override fun dismiss(requestId: String) {
        mainHandler.post { hideInternal(requestId) }
    }

    @SuppressLint("InflateParams")
    private fun showInternal(request: PauseRequest, callbacks: PausePresentationCallbacks) {
        if (active != null || root != null) {
            callbacks.onPresentationLost(
                request.id,
                IllegalStateException("A pause presentation is already active"),
            )
            return
        }

        val appName = resolveAppName(request.packageName)
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
                        appScope = appScope,
                        coordinator = coordinatorProvider.get(),
                    )
                }
            },
        )[PauseViewModel::class.java]
        viewModel.init(request.id, request.packageName, appName)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(overlayOwners)
            setViewTreeViewModelStoreOwner(overlayOwners)
            setViewTreeSavedStateRegistryOwner(overlayOwners)
            setContent {
                BreatheTheme {
                    PauseOverlayContent(
                        appName = appName,
                        viewModel = viewModel,
                        onYes = viewModel::recordOpened,
                        onNo = {
                            viewModel.recordDeclined()
                            goHome()
                        },
                    )
                }
            }
        }

        val container = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    coordinatorProvider.get().dismissWithBack(request.id)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            addView(composeView)
            isFocusableInTouchMode = true
            requestFocus()
        }
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                if (root === container && active?.requestId == request.id) {
                    clearOverlayState()
                    callbacks.onPresentationLost(request.id, null)
                }
            }
        })

        try {
            windowManager.addView(container, layoutParams())
        } catch (cause: RuntimeException) {
            Log.w(TAG, "Overlay rejected; falling back to the pause activity", cause)
            overlayOwners.destroy()
            active = ActivePresentation(request.id)
            callbacks.onOverlayFailed(request.id, cause)
            launchPauseActivity(request, callbacks)
            return
        }

        root = container
        owners = overlayOwners
        active = ActivePresentation(request.id)
        isShowing = true
        callbacks.onOverlayAdded(request.id)
    }

    private fun hideInternal(requestId: String) {
        val current = active ?: return
        if (current.requestId != requestId) return
        val container = root
        clearOverlayState()
        if (container != null) {
            runCatching { windowManager.removeView(container) }
                .onFailure { Log.w(TAG, "Overlay already detached", it) }
        } else {
            activityRequests.close(requestId)
        }
    }

    private fun clearOverlayState() {
        owners?.destroy()
        owners = null
        root = null
        active = null
        isShowing = false
    }

    private fun launchPauseActivity(
        request: PauseRequest,
        callbacks: PausePresentationCallbacks,
    ) {
        activityRequests.open(request.id)
        try {
            context.startActivity(
                PauseActivity.newIntent(context, request.packageName, request.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        } catch (cause: RuntimeException) {
            activityRequests.close(request.id)
            callbacks.onPresentationLost(request.id, cause)
        }
    }

    private fun resolveAppName(packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    )

    private fun goHome() {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private data class ActivePresentation(
        val requestId: String,
    )

    private class OverlayOwners : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun create() {
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}

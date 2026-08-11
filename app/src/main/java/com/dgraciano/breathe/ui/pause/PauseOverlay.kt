package com.dgraciano.breathe.ui.pause

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts the pause UI in a [WindowManager] overlay rather than an Activity.
 *
 * This is what makes SYSTEM_ALERT_WINDOW an honest permission: the previous design held
 * the permission solely as a background-activity-launch exemption while never drawing an
 * overlay. Drawing a real overlay removes the BAL dependency entirely.
 *
 * Compose outside an Activity has no lifecycle, saved-state or view-model owner attached
 * to the window, so this class supplies all three.
 */
class PauseOverlay(private val context: Context) :
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: OverlayRoot? = null

    val isShowing: Boolean get() = root != null

    /**
     * @param onBackPressed invoked for the hardware/gesture back action. The overlay does
     * not dismiss itself — the caller decides, so the intervention is still recorded.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show(onBackPressed: () -> Unit, content: @Composable () -> Unit) {
        if (root != null) return

        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val overlayRoot = OverlayRoot(context, onBackPressed).apply {
            setViewTreeLifecycleOwner(this@PauseOverlay)
            setViewTreeSavedStateRegistryOwner(this@PauseOverlay)
            setViewTreeViewModelStoreOwner(this@PauseOverlay)
        }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@PauseOverlay)
            setViewTreeSavedStateRegistryOwner(this@PauseOverlay)
            setViewTreeViewModelStoreOwner(this@PauseOverlay)
            setContent(content)
        }
        overlayRoot.addView(composeView)

        windowManager.addView(overlayRoot, buildLayoutParams())
        root = overlayRoot

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        overlayRoot.requestFocus()
    }

    fun dismiss() {
        val current = root ?: return
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        runCatching { windowManager.removeViewImmediate(current) }
        store.clear()
        root = null
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Focusable so the overlay receives the back key; the pause is modal by design.
            // FLAG_SECURE keeps the blocked app's name out of screenshots and recents.
            WindowManager.LayoutParams.FLAG_SECURE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * Intercepts the back key. An overlay has no Activity dispatching to Compose's
     * BackHandler, so the key has to be caught at the view root.
     */
    private class OverlayRoot(
        context: Context,
        private val onBackPressed: () -> Unit
    ) : FrameLayout(context) {

        init {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBackPressed()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }
}

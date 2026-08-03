package com.dgraciano.breathe.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.ui.pause.PauseActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BreatheAccessibilityService : AccessibilityService() {
    @Inject lateinit var appRepository: AppRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selectedPackages = AtomicReference<Set<String>>(emptySet())
    private lateinit var coordinator: AccessibilityPauseCoordinator

    override fun onServiceConnected() {
        super.onServiceConnected()
        coordinator = AccessibilityPauseCoordinator(
            gate = ForegroundSessionGate(packageName),
            selectedPackages = selectedPackages::get,
            launchPause = ::launchPause
        )
        scope.launch {
            appRepository.getBlockedApps()
                .map { apps -> apps.mapTo(mutableSetOf()) { it.packageName } }
                .distinctUntilChanged()
                .collect(selectedPackages::set)
        }
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        coordinator.onWindowStateChanged(event.packageName?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        Log.i(TAG, "destroyed")
        super.onDestroy()
    }

    private fun launchPause(request: PauseRequest) {
        val intent = PauseActivity.newIntent(this, request.packageName).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        runCatching { startActivity(intent) }
            .onSuccess { Log.i(TAG, "launched session=${request.sessionId}") }
            .onFailure { error -> Log.e(TAG, "launch failed session=${request.sessionId}", error) }
    }

    private companion object {
        const val TAG = "BreatheA11y"
    }
}

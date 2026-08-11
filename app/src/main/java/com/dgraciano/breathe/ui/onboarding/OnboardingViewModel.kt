package com.dgraciano.breathe.ui.onboarding

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.dgraciano.breathe.service.BreatheAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _hasUsagePermission = MutableStateFlow(false)
    val hasUsagePermission: StateFlow<Boolean> = _hasUsagePermission

    private val _hasOverlayPermission = MutableStateFlow(false)
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission

    /** The one that actually powers interception now that polling is gone. */
    private val _hasAccessibility = MutableStateFlow(false)
    val hasAccessibility: StateFlow<Boolean> = _hasAccessibility

    // Resolved synchronously at construction so the nav graph can pick a start
    // destination on first composition instead of always opening onboarding and
    // bouncing to home.
    init { refreshPermissionState() }

    fun refreshPermissionState() {
        _hasUsagePermission.value = checkUsagePermission()
        _hasOverlayPermission.value = checkOverlayPermission()
        _hasAccessibility.value = BreatheAccessibilityService.isEnabled(context)
    }

    private fun checkUsagePermission(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        // unsafeCheckOpNoThrow is API 29+; checkOpNoThrow is the pre-29 equivalent.
        // Calling the former on minSdk 26 crashed onboarding on Android 8-9.
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }
}

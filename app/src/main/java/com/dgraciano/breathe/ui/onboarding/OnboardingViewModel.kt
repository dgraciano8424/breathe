package com.dgraciano.breathe.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import com.dgraciano.breathe.service.accessibility.AccessibilityServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _hasAccessibilityPermission = MutableStateFlow(false)
    val hasAccessibilityPermission: StateFlow<Boolean> = _hasAccessibilityPermission

    init {
        refreshPermissionState()
    }

    fun refreshPermissionState() {
        _hasAccessibilityPermission.value = AccessibilityServiceState.isEnabled(context)
    }
}

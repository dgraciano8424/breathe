package com.dgraciano.breathe.service.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityServiceState {
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(
            context,
            BreatheAccessibilityService::class.java
        ).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return containsService(enabled, expected)
    }

    internal fun containsService(enabled: String?, expected: String): Boolean =
        enabled
            ?.split(':')
            ?.any { component -> component.equals(expected, ignoreCase = true) }
            ?: false
}

package com.dgraciano.breathe.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** Reads Android's enabled-service setting without accepting package or class-name prefixes. */
object AccessibilityServiceState {

    fun isEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = ComponentName(context, BreatheAccessibilityService::class.java)
        return isEnabled(enabledServices, expected.packageName, expected.className)
    }

    internal fun isEnabled(
        enabledServices: String?,
        applicationPackage: String,
        serviceClassName: String,
    ): Boolean = enabledServices
        ?.split(':')
        ?.any { flattened ->
            val separator = flattened.indexOf('/')
            if (separator <= 0 || separator == flattened.lastIndex) return@any false

            val packageName = flattened.substring(0, separator)
            val encodedClassName = flattened.substring(separator + 1)
            val className = if (encodedClassName.startsWith('.')) {
                packageName + encodedClassName
            } else {
                encodedClassName
            }
            packageName == applicationPackage && className == serviceClassName
        }
        ?: false
}

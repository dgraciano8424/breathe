package com.dgraciano.breathe.ui.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.dgraciano.breathe.service.BreatheAccessibilityService

/**
 * Opens accessibility settings as close to Breathe's own switch as the platform allows.
 *
 * Accessibility has no consent dialog. Unlike camera or location there is no system prompt
 * an app can raise — the user has to find a switch in Settings — so the only things we
 * control are where they land and how well prepared they are when they get there.
 *
 * **Why this does not land on Breathe's own page.** Android has an intent for exactly
 * that, `android.settings.ACCESSIBILITY_DETAILS_SETTINGS`, which opens a screen holding
 * one switch and nothing else. It is not usable here, for two independent reasons:
 * the constant is not in the public SDK, and the activity behind it is guarded by
 * `android.permission.OPEN_ACCESSIBILITY_DETAILS_SETTINGS`, whose protection level is
 * `signature|installer`. That permission is declared by the platform, so the restriction
 * is the same on every device rather than an OEM quirk — a third-party app cannot hold it
 * and the call fails with a SecurityException. Verified on One UI 8.0.5 / Android 16.
 * Do not add it back expecting a different result.
 *
 * So: ask Settings to highlight our row, and fall back to the plain screen.
 *
 * The `:settings:fragment_args_key` extras are honoured by AOSP-style Settings, which
 * scrolls to and highlights the entry. They are verified to do nothing on One UI, which
 * shows the top-level accessibility page regardless — leaving the user two taps from the
 * switch, via "Installed apps" and then Breathe. Those two taps cannot be removed, which
 * is why [AccessibilityDisclosureDialog] lists them before this is called.
 */
internal fun openAccessibilitySettings(context: Context) {
    val flattened = ComponentName(context, BreatheAccessibilityService::class.java)
        .flattenToString()

    val highlighted = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .putExtra(SETTINGS_ARGS_KEY, flattened)
        .putExtra(SETTINGS_SHOW_ARGS, Bundle().apply { putString(SETTINGS_ARGS_KEY, flattened) })
    if (startOrNull(context, highlighted)) return

    startOrNull(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

/**
 * Returns whether the activity started. Catches rather than throws because the failure has
 * a usable fallback, and crashing on the way to a settings screen would be a worse outcome
 * than a less specific settings screen.
 */
private fun startOrNull(context: Context, intent: Intent): Boolean = runCatching {
    context.startActivity(intent)
}.onFailure {
    Log.w(TAG, "Could not open ${intent.action}", it)
}.isSuccess

private const val SETTINGS_ARGS_KEY = ":settings:fragment_args_key"
private const val SETTINGS_SHOW_ARGS = ":settings:show_fragment_args"
private const val TAG = "AccessibilityLaunch"

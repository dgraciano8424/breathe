package com.dgraciano.breathe.ui.util

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user has turned animations down or off system-wide.
 *
 * Breathe previously ran four always-on infinite animations regardless of this setting,
 * including a canvas wave redrawn every frame on every screen — a vestibular trigger and
 * a constant battery cost.
 *
 * Note this suppresses *decorative* motion only. The breathing circle on the pause screen
 * is functional content — it is the pacing cue the user is there for — so it keeps
 * animating.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

package com.dgraciano.breathe.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user has turned animations off system-wide (Developer options, or the
 * "Remove animations" accessibility setting, which sets the same scale to zero).
 *
 * Infinite animations are the ones that matter here: a cloud that never stops drifting
 * and a breathing ring that pulses forever are exactly what someone with motion
 * sensitivity is asking the system to stop.
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

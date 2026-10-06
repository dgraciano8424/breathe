package com.dgraciano.breathe.ui.components

import android.provider.Settings
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    fun read() = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    var reduced by remember(context) { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        }.isSuccess
        reduced = read()
        onDispose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

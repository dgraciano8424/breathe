package com.dgraciano.breathe.service

import android.os.SystemClock
import javax.inject.Inject

/** Monotonic elapsed time; changing the device date cannot skip a pause. */
class PauseClock @Inject constructor() {
    fun now(): Long = SystemClock.elapsedRealtime()
}

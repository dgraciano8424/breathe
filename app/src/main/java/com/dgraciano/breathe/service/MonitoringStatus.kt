package com.dgraciano.breathe.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class MonitoringSnapshot(
    val connected: Boolean = false,
    val appsLoaded: Boolean = false,
    val lastPauseAt: Long? = null,
    val issue: String? = null
)

/** Process-local diagnostics. Never stores app names, screen content or visit history. */
@Singleton
class MonitoringStatus @Inject constructor() {
    private val mutable = MutableStateFlow(MonitoringSnapshot())
    val state: StateFlow<MonitoringSnapshot> = mutable
    fun connected() { mutable.value = mutable.value.copy(connected = true, appsLoaded = false, issue = null) }
    fun loaded() { mutable.value = mutable.value.copy(appsLoaded = true) }
    fun disconnected() { mutable.value = mutable.value.copy(connected = false, appsLoaded = false) }
    fun pauseShown(now: Long = System.currentTimeMillis()) { mutable.value = mutable.value.copy(lastPauseAt = now, issue = null) }
    fun failed() { mutable.value = mutable.value.copy(issue = "Android could not display the pause. Check display-over-other-apps access, then test again.") }
}

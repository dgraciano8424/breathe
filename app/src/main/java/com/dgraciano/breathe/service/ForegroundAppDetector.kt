package com.dgraciano.breathe.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import javax.inject.Inject

class ForegroundAppDetector @Inject constructor(
    private val usageStatsManager: UsageStatsManager
) {
    fun getCurrentApp(): String? {
        val now = System.currentTimeMillis()
        val start = now - 5000L // Look back 5 seconds
        val events = usageStatsManager.queryEvents(start, now)
        val event = UsageEvents.Event()
        var lastPackageName: String? = null
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackageName = event.packageName
            }
        }
        return lastPackageName
    }
}

package com.dgraciano.breathe.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import javax.inject.Inject

class ForegroundAppDetector @Inject constructor(
    private val usageStatsManager: UsageStatsManager
) {
    fun getCurrentApp(): String? {
        val now = System.currentTimeMillis()
        // Increase window to 10 seconds for reliability on emulators/slow devices
        val start = now - 10_000L 
        
        val events = usageStatsManager.queryEvents(start, now)
        val event = UsageEvents.Event()
        var lastPackageName: String? = null
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // ACTIVITY_RESUMED (1) is more reliable than MOVE_TO_FOREGROUND on some APIs
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || 
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackageName = event.packageName
            }
        }
        return lastPackageName
    }
}

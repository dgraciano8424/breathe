package com.dgraciano.breathe.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_SESSION_MINUTES = 20
private val TWENTY_EIGHT_DAYS_MS = TimeUnit.DAYS.toMillis(28)

@Singleton
class SessionTimeHelper @Inject constructor(
    private val usageStatsManager: UsageStatsManager
) {
    private val cache = mutableMapOf<String, Int>()

    fun getAvgSessionMinutes(packageName: String): Int {
        cache[packageName]?.let { return it }
        val result = computeAvgSessionMinutes(packageName)
        cache[packageName] = result
        return result
    }

    private fun computeAvgSessionMinutes(packageName: String): Int {
        val now = System.currentTimeMillis()
        val start = now - TWENTY_EIGHT_DAYS_MS

        val events = usageStatsManager.queryEvents(start, now)
        val event = UsageEvents.Event()

        var totalMs = 0L
        var sessionCount = 0
        var foregroundAt = -1L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foregroundAt = event.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (foregroundAt > 0) {
                        val duration = event.timeStamp - foregroundAt
                        if (duration in 5_000..7_200_000) { // 5s–2h sanity window
                            totalMs += duration
                            sessionCount++
                        }
                        foregroundAt = -1L
                    }
                }
            }
        }

        if (sessionCount == 0) return DEFAULT_SESSION_MINUTES
        val avgMs = totalMs / sessionCount
        return maxOf(1, (avgMs / 60_000).toInt())
    }
}

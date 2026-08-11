package com.dgraciano.breathe.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_SESSION_MINUTES = 20

// Narrowed from 28 days to 7. The average is used only to estimate minutes saved, and a
// week of history is ample for that — a four-week cursor was four times the work for no
// meaningful gain in accuracy.
private val USAGE_WINDOW_MS = TimeUnit.DAYS.toMillis(7)

/** Averages drift slowly; recomputing more than hourly is wasted work. */
private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(1)

@Singleton
class SessionTimeHelper @Inject constructor(
    private val usageStatsManager: UsageStatsManager
) {
    private data class CachedAverage(val minutes: Int, val computedAt: Long)

    // ConcurrentHashMap: this is a @Singleton reached from the accessibility service and
    // from ViewModel coroutines, so a plain mutableMap here was an unsynchronised shared map.
    private val cache = ConcurrentHashMap<String, CachedAverage>()

    fun getAvgSessionMinutes(packageName: String): Int {
        val now = System.currentTimeMillis()
        cache[packageName]?.let { cached ->
            if (now - cached.computedAt < CACHE_TTL_MS) return cached.minutes
        }
        val result = computeAvgSessionMinutes(packageName)
        cache[packageName] = CachedAverage(result, now)
        return result
    }

    /**
     * Scans usage history for this package. Callers must run this off the main thread —
     * the window is measured in weeks and the event cursor covers every app on the device.
     */
    private fun computeAvgSessionMinutes(packageName: String): Int {
        val now = System.currentTimeMillis()
        val start = now - USAGE_WINDOW_MS

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

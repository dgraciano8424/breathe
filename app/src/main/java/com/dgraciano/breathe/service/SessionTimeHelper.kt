package com.dgraciano.breathe.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_SESSION_MINUTES = 20

/**
 * How far back to look for completed sessions. The platform only retains usage
 * *events* for about a week, so a longer window mostly costs iteration without
 * yielding more data — and a week tracks recent habits more closely anyway.
 */
private val LOOKBACK_MS = TimeUnit.DAYS.toMillis(7)

/**
 * How long a computed average stays fresh. Session habits move slowly, so this only
 * needs to be short enough that the figure tracks a changing routine within a day.
 */
private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(6)

/** Newest sessions to average over, so a heavy user's history can't grow the work. */
private const val MAX_SESSIONS = 50

@Singleton
class SessionTimeHelper @Inject constructor(
    private val usageStatsManager: UsageStatsManager
) {
    private class Entry(val minutes: Int, val computedAt: Long)

    /** Concurrent because declines are recorded on a shared application scope. */
    private val cache = ConcurrentHashMap<String, Entry>()

    /** Overridable for tests; production always reads the wall clock. */
    internal var clock: () -> Long = System::currentTimeMillis

    fun getAvgSessionMinutes(packageName: String): Int {
        val now = clock()
        cache[packageName]?.let { if (now - it.computedAt < CACHE_TTL_MS) return it.minutes }

        val result = computeAvgSessionMinutes(packageName, now)
        cache[packageName] = Entry(result, now)
        return result
    }

    private fun computeAvgSessionMinutes(packageName: String, now: Long): Int {
        val events = usageStatsManager.queryEvents(now - LOOKBACK_MS, now)
        val event = UsageEvents.Event()

        // Ring buffer of the most recent session durations: the cursor runs
        // oldest-first, so keeping the newest MAX_SESSIONS means overwriting in place.
        val durations = LongArray(MAX_SESSIONS)
        var seen = 0
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
                            durations[seen % MAX_SESSIONS] = duration
                            seen++
                        }
                        foregroundAt = -1L
                    }
                }
            }
        }

        if (seen == 0) return DEFAULT_SESSION_MINUTES
        val count = minOf(seen, MAX_SESSIONS)
        var totalMs = 0L
        for (i in 0 until count) totalMs += durations[i]
        return maxOf(1, ((totalMs / count) / 60_000).toInt())
    }
}

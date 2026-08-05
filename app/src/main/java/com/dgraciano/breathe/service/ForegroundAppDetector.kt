package com.dgraciano.breathe.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** How far back to look while we have never observed a single usage event. */
private val COLD_START_LOOKBACK_MS = TimeUnit.HOURS.toMillis(24)

/**
 * Minimum gap between wide lookback scans. Without usage-access permission every
 * query comes back empty, and the poll loop runs twice a second — this keeps that
 * case from re-scanning 24 hours of history on every tick.
 */
private const val WIDE_RESCAN_INTERVAL_MS = 5_000L

/** Re-read a sliver of the previous window, in case an event landed late. */
private const val WINDOW_OVERLAP_MS = 1_000L

/**
 * Tracks which app is in the foreground.
 *
 * The usage-events API only reports *transitions*, so a naive "what happened in the
 * last few seconds" query returns nothing whenever the user has been sitting in one
 * app for a while — and the caller cannot distinguish "nothing changed" from "no app".
 * This detector instead remembers the last app it saw resume and reports that until a
 * newer transition replaces it, so a restart, an unlock, a delayed poll, or a late
 * permission grant cannot strand it on a stale `null`.
 */
@Singleton
class ForegroundAppDetector @Inject constructor(
    private val usageStatsManager: UsageStatsManager
) {
    /** Overridable for tests; production always reads the wall clock. */
    internal var clock: () -> Long = System::currentTimeMillis

    private var lastKnownPackage: String? = null

    /** End of the last window consumed; 0 until we have observed an event. */
    private var windowEnd = 0L
    private var lastWideScanAt = 0L

    fun getCurrentApp(): String? {
        val now = clock()
        val start = windowStart(now) ?: return lastKnownPackage

        val events = usageStatsManager.queryEvents(start, now)
        val event = UsageEvents.Event()
        var latest: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // ACTIVITY_RESUMED is more reliable than MOVE_TO_FOREGROUND on some APIs.
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                latest = event.packageName
            }
        }

        if (latest != null) {
            lastKnownPackage = latest
            // Only start tracking incrementally once we have real data. Until then
            // every scan stays wide, so a permission granted after the first poll
            // still recovers the app that is already on screen.
            windowEnd = now
        }
        return lastKnownPackage
    }

    /**
     * Start of the window to query, or null if a wide scan is due but throttled —
     * in which case the caller keeps the last known value.
     */
    private fun windowStart(now: Long): Long? {
        if (windowEnd > 0L) return windowEnd - WINDOW_OVERLAP_MS

        if (now - lastWideScanAt < WIDE_RESCAN_INTERVAL_MS && lastWideScanAt > 0L) return null
        lastWideScanAt = now
        return now - COLD_START_LOOKBACK_MS
    }

    /** Drops cached state, forcing the next call to scan wide again. */
    fun reset() {
        lastKnownPackage = null
        windowEnd = 0L
        lastWideScanAt = 0L
    }
}

package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.InterventionEventDao
import com.dgraciano.breathe.data.model.AppStat
import com.dgraciano.breathe.data.model.InterventionEvent
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

internal fun mondayStart(now: Calendar): Long = (now.clone() as Calendar).apply {
    add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Singleton
class StatsRepository @Inject constructor(private val dao: InterventionEventDao) {

    internal var clock: () -> ZonedDateTime = { ZonedDateTime.now() }

    private fun startOfToday(): Long {
        val now = clock()
        return now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
    }

    private fun startOfWeek(): Long {
        val now = clock()
        return now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(now.zone).toInstant().toEpochMilli()
    }

    suspend fun getTodayAttemptCount(packageName: String): Int =
        dao.getAttemptCount(packageName, startOfToday())

    suspend fun getTodayTotalAttempts(): Int = dao.getTotalAttempts(startOfToday())

    suspend fun getTodayDeclined(): Int = dao.getTotalDeclined(startOfToday())

    suspend fun getWeeklyTotalAttempts(): Int = dao.getTotalAttempts(startOfWeek())

    suspend fun getWeeklyDeclined(): Int = dao.getTotalDeclined(startOfWeek())

    suspend fun getTodayMinutesSaved(): Int = dao.getTotalMinutesSavedSince(startOfToday())

    suspend fun getWeeklyMinutesSaved(): Int = dao.getTotalMinutesSavedSince(startOfWeek())

    suspend fun getTopAppsThisWeek(): List<AppStat> = dao.getTopApps(startOfWeek())

    suspend fun getFocusStreak(): Int {
        val all = dao.getAllOrdered()
        var streak = 0
        for (event in all) {
            if (event.outcome == "DECLINED") streak++
            else break
        }
        return streak
    }

    fun getRecentEvents(): Flow<List<InterventionEvent>> = dao.getRecent()

    suspend fun recordEvent(event: InterventionEvent) = dao.insert(event)

    suspend fun getHistory(): List<InterventionEvent> = dao.getAllOrdered()

    /** Deletes choices only; monitored apps and their settings belong to another table. */
    suspend fun clearHistory() = dao.clearHistory()
}

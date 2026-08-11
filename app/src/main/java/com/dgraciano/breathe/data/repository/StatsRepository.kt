package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.InterventionEventDao
import com.dgraciano.breathe.data.model.AppStat
import com.dgraciano.breathe.data.model.InterventionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(private val dao: InterventionEventDao) {

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfWeek(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    suspend fun getTodayAttemptCount(packageName: String): Int =
        dao.getAttemptCount(packageName, startOfToday())

    suspend fun getTodayTotalAttempts(): Int = dao.getTotalAttempts(startOfToday())

    suspend fun getTodayDeclined(): Int = dao.getTotalDeclined(startOfToday())

    suspend fun getWeeklyTotalAttempts(): Int = dao.getTotalAttempts(startOfWeek())

    suspend fun getWeeklyDeclined(): Int = dao.getTotalDeclined(startOfWeek())

    suspend fun getTodayMinutesSaved(): Int = dao.getTotalMinutesSavedSince(startOfToday())

    suspend fun getWeeklyMinutesSaved(): Int = dao.getTotalMinutesSavedSince(startOfWeek())

    suspend fun getTopAppsThisWeek(): List<AppStat> = dao.getTopApps(startOfWeek())

    suspend fun getFocusStreak(): Int = withContext(Dispatchers.IO) {
        dao.getCurrentDeclineStreak()
    }

    fun getRecentEvents(): Flow<List<InterventionEvent>> = dao.getRecent()

    suspend fun recordEvent(event: InterventionEvent) = withContext(Dispatchers.IO) {
        dao.insert(event)
        pruneOldEvents()
    }

    /**
     * Keeps the table bounded. Previously it grew forever, which slowed every query and
     * retained behavioural history indefinitely with no way for the user to clear it.
     */
    private suspend fun pruneOldEvents() {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        runCatching { dao.deleteOlderThan(cutoff) }
    }

    companion object {
        /** One year is well beyond what any screen reads (longest window is a week). */
        val RETENTION_MS = TimeUnit.DAYS.toMillis(365)
    }
}

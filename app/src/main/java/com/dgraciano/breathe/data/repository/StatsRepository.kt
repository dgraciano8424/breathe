package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.InterventionEventDao
import com.dgraciano.breathe.data.model.AppStat
import com.dgraciano.breathe.data.model.InterventionEvent
import kotlinx.coroutines.flow.Flow
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

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfWeek(): Long = mondayStart(Calendar.getInstance())

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
}

package com.dgraciano.breathe.data.db

import androidx.room.*
import com.dgraciano.breathe.data.model.AppStat
import com.dgraciano.breathe.data.model.InterventionEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface InterventionEventDao {

    @Insert
    suspend fun insert(event: InterventionEvent)

    @Query("SELECT COUNT(*) FROM intervention_events WHERE packageName = :pkg AND timestamp > :since")
    suspend fun getAttemptCount(pkg: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM intervention_events WHERE timestamp > :since")
    suspend fun getTotalAttempts(since: Long): Int

    @Query("SELECT COUNT(*) FROM intervention_events WHERE timestamp > :since AND outcome = 'DECLINED'")
    suspend fun getTotalDeclined(since: Long): Int

    @Query("""
        SELECT packageName, appName, COUNT(*) as count
        FROM intervention_events
        WHERE timestamp > :since
        GROUP BY packageName
        ORDER BY count DESC
        LIMIT 5
    """)
    suspend fun getTopApps(since: Long): List<AppStat>

    @Query("SELECT * FROM intervention_events ORDER BY timestamp DESC LIMIT 100")
    fun getRecent(): Flow<List<InterventionEvent>>

    /**
     * Consecutive declines since the last time the user opened something.
     *
     * Replaces loading the entire table and counting a prefix in Kotlin; this is exact
     * and served by the (outcome, timestamp) index.
     */
    @Query("""
        SELECT COUNT(*) FROM intervention_events
        WHERE outcome = 'DECLINED'
          AND timestamp > (
            SELECT COALESCE(MAX(timestamp), 0) FROM intervention_events WHERE outcome = 'OPENED'
          )
    """)
    suspend fun getCurrentDeclineStreak(): Int

    /** Retention: history older than the cutoff is of no use to any screen. */
    @Query("DELETE FROM intervention_events WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT COALESCE(SUM(minutesSaved), 0) FROM intervention_events WHERE outcome = 'DECLINED' AND timestamp > :since")
    suspend fun getTotalMinutesSavedSince(since: Long): Int

    @Query("SELECT COALESCE(SUM(minutesSaved), 0) FROM intervention_events WHERE outcome = 'DECLINED'")
    suspend fun getTotalMinutesSaved(): Long

    @Query("SELECT COUNT(*) FROM intervention_events WHERE outcome = 'DECLINED'")
    suspend fun getLifetimeDeclined(): Long
}

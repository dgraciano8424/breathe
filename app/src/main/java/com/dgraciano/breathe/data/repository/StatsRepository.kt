package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.InterventionEventDao
import com.dgraciano.breathe.data.db.LifetimeAggregate
import com.dgraciano.breathe.data.db.TodayAggregate
import com.dgraciano.breathe.data.model.AppStat
import com.dgraciano.breathe.data.model.InterventionEvent
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository internal constructor(
    private val dao: InterventionEventDao,
    private val clock: Clock,
    private val zone: ZoneId,
    private val dayRollovers: Flow<Unit>,
) {

    @Inject
    constructor(dao: InterventionEventDao) : this(
        dao = dao,
        clock = Clock.systemUTC(),
        zone = ZoneId.systemDefault(),
    )

    internal constructor(
        dao: InterventionEventDao,
        clock: Clock,
        zone: ZoneId,
    ) : this(dao, clock, zone, localMidnightTicks(clock, zone))

    private fun startOfToday(): Long =
        LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun startOfWeek(): Long = LocalDate.now(clock.withZone(zone))
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    suspend fun getTodayAttemptCount(packageName: String): Int =
        dao.getAttemptCount(packageName, startOfToday())

    suspend fun getTodayTotalAttempts(): Int = dao.getTotalAttempts(startOfToday())

    suspend fun getTodayDeclined(): Int = dao.getTotalDeclined(startOfToday())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayAggregate(): Flow<TodayAggregate> = flow {
        emitAll(
            dayRollovers
                .onStart { emit(Unit) }
                .flatMapLatest { dao.observeTodayAggregate(startOfToday()) }
        )
    }

    fun observeLifetimeAggregate(): Flow<LifetimeAggregate> =
        dao.observeLifetimeAggregate()

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

    suspend fun recordEvent(event: InterventionEvent) {
        dao.insert(event)
    }
}

internal fun localMidnightTicks(clock: Clock, zone: ZoneId): Flow<Unit> = flow {
    while (true) {
        val now = ZonedDateTime.ofInstant(clock.instant(), zone)
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
        val delayMillis = Duration.between(now.toInstant(), nextMidnight.toInstant())
            .toMillis()
            .coerceAtLeast(1)
        delay(delayMillis)
        emit(Unit)
    }
}

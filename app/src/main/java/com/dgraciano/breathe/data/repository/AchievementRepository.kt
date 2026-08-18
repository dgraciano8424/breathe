package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.model.Achievements
import com.dgraciano.breathe.data.model.UserProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementRepository @Inject constructor(
    private val statsRepository: StatsRepository,
) {
    fun observeUserProgress(): Flow<UserProgress> =
        statsRepository.observeLifetimeAggregate().map { aggregate ->
            userProgress(aggregate.minutesSaved, aggregate.declined)
        }

    private fun userProgress(totalMinutes: Long, declines: Long): UserProgress {
        val level        = Achievements.computeLevel(totalMinutes)
        val next         = Achievements.nextLevel(level)
        return UserProgress(
            totalMinutesSaved = totalMinutes,
            lifetimeDeclines  = declines,
            currentLevel      = level,
            nextLevel         = next,
            progressToNext    = Achievements.progressToNext(totalMinutes, level, next),
            badges            = Achievements.computeBadges(totalMinutes, declines)
        )
    }
}

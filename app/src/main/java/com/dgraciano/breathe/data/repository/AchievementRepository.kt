package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.InterventionEventDao
import com.dgraciano.breathe.data.model.Achievements
import com.dgraciano.breathe.data.model.UserProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementRepository @Inject constructor(
    private val dao: InterventionEventDao
) {
    suspend fun getUserProgress(): UserProgress {
        val totalMinutes = dao.getTotalMinutesSaved()
        val declines     = dao.getLifetimeDeclined()
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

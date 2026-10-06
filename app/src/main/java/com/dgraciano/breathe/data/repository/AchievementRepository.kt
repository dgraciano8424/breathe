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
        val totals = dao.getProgressTotals()
        val level        = Achievements.computeLevel(totals.activeDays)
        val next         = Achievements.nextLevel(level)
        return UserProgress(
            totalMinutesSaved = totals.estimatedMinutes,
            lifetimeDeclines  = totals.declined,
            activeDays = totals.activeDays,
            lifetimeChoices = totals.choices,
            currentLevel      = level,
            nextLevel         = next,
            progressToNext    = Achievements.progressToNext(totals.activeDays, level, next),
            badges            = Achievements.computeBadges(totals.choices, totals.activeDays)
        )
    }
}

package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.InterventionEventDao
import com.dgraciano.breathe.data.model.Achievements
import com.dgraciano.breathe.data.model.ProgressTotals
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AchievementRepositoryTest {
    @Test fun `continuing choices count without estimated savings`() = runTest {
        val dao = mockk<InterventionEventDao>()
        coEvery { dao.getProgressTotals() } returns ProgressTotals(10, 3, 0, 0)
        val progress = AchievementRepository(dao).getUserProgress()
        assertEquals("Sapling", progress.currentLevel.name)
        assertEquals(10L, progress.lifetimeChoices)
        assertEquals(3L, progress.activeDays)
        assertTrue(progress.badges.first { it.id == "ten_choices" }.unlocked)
    }
    @Test fun `large legacy savings do not inflate milestones`() = runTest {
        val dao = mockk<InterventionEventDao>()
        coEvery { dao.getProgressTotals() } returns ProgressTotals(1, 1, 1, 50000)
        val progress = AchievementRepository(dao).getUserProgress()
        assertEquals("Sprout", progress.currentLevel.name)
        assertEquals(50000L, progress.totalMinutesSaved)
        assertFalse(progress.badges.first { it.id == "century" }.unlocked)
    }
    @Test fun `day boundaries and final level have bounded progress`() {
        assertEquals("Seedling", Achievements.computeLevel(0).name)
        assertEquals("Sapling", Achievements.computeLevel(6).name)
        assertEquals("Tree", Achievements.computeLevel(7).name)
        val current = Achievements.computeLevel(5)
        assertEquals(0.5f, Achievements.progressToNext(5, current, Achievements.nextLevel(current)))
        val last = Achievements.computeLevel(100)
        assertNull(Achievements.nextLevel(last))
        assertEquals(1f, Achievements.progressToNext(100, last, null))
    }
}

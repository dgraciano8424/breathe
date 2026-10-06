package com.dgraciano.breathe.data.model

data class Level(val index: Int, val name: String, val emoji: String, val description: String, val minDays: Long)
data class MilestoneBadge(val id: String, val name: String, val emoji: String, val description: String, val unlocked: Boolean)
data class UserProgress(
    val totalMinutesSaved: Long,
    val lifetimeDeclines: Long,
    val currentLevel: Level,
    val nextLevel: Level?,
    val progressToNext: Float,
    val badges: List<MilestoneBadge>,
    val activeDays: Long = 0,
    val lifetimeChoices: Long = 0
)

/** Milestones depend on recorded choices and calendar days, never estimated savings. */
object Achievements {
    val LEVELS = listOf(
        Level(0, "Seedling", "🌱", "Begin with one small pause", 0),
        Level(1, "Sprout", "🌿", "A choice on your first day", 1),
        Level(2, "Sapling", "🌳", "Choices on 3 different days", 3),
        Level(3, "Tree", "🌲", "Choices on 7 different days", 7),
        Level(4, "Summit", "🏔️", "Choices on 14 different days", 14),
        Level(5, "Flow", "🌊", "Choices on 30 different days", 30),
        Level(6, "Stellar", "⭐", "Choices on 60 different days", 60),
        Level(7, "Steady", "🌌", "Choices on 100 different days", 100)
    )
    fun computeLevel(activeDays: Long): Level = LEVELS.lastOrNull { activeDays >= it.minDays } ?: LEVELS.first()
    fun nextLevel(current: Level): Level? = LEVELS.getOrNull(current.index + 1)
    fun progressToNext(activeDays: Long, current: Level, next: Level?): Float {
        if (next == null) return 1f
        return ((activeDays - current.minDays).toFloat() / (next.minDays - current.minDays)).coerceIn(0f, 1f)
    }
    fun computeBadges(choices: Long, activeDays: Long): List<MilestoneBadge> = listOf(
        MilestoneBadge("first_choice", "First choice", "🌬️", "Made your first choice after a pause", choices >= 1),
        MilestoneBadge("ten_choices", "Ten choices", "🔟", "Made 10 choices after a pause", choices >= 10),
        MilestoneBadge("three_days", "Finding a rhythm", "🌿", "Made a choice on 3 different days", activeDays >= 3),
        MilestoneBadge("seven_days", "Seven days", "📅", "Made a choice on 7 different days", activeDays >= 7),
        MilestoneBadge("century", "One hundred", "💯", "Made 100 choices after a pause", choices >= 100),
        MilestoneBadge("thirty_days", "Showing up", "🗓️", "Made a choice on 30 different days", activeDays >= 30),
        MilestoneBadge("five_hundred", "Five hundred", "🎯", "Made 500 choices after a pause", choices >= 500),
        MilestoneBadge("hundred_days", "Steady rhythm", "🌙", "Made a choice on 100 different days", activeDays >= 100)
    )
}

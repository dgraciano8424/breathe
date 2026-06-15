package com.dgraciano.breathe.data.model

data class Level(
    val index: Int,
    val name: String,
    val emoji: String,
    val description: String,
    val minMinutes: Long
)

data class MilestoneBadge(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val unlocked: Boolean
)

data class UserProgress(
    val totalMinutesSaved: Long,
    val lifetimeDeclines: Long,
    val currentLevel: Level,
    val nextLevel: Level?,
    val progressToNext: Float,
    val badges: List<MilestoneBadge>
) {
    val hoursDisplay: String get() = when {
        totalMinutesSaved < 60 -> "${totalMinutesSaved}m"
        totalMinutesSaved < 1440 -> "${totalMinutesSaved / 60}h ${totalMinutesSaved % 60}m"
        else -> "${totalMinutesSaved / 1440}d ${(totalMinutesSaved % 1440) / 60}h"
    }
}

object Achievements {
    val LEVELS = listOf(
        Level(0, "Seedling",    "🌱", "Just getting started",            0),
        Level(1, "Sprout",      "🌿", "30 minutes reclaimed",            30),
        Level(2, "Sapling",     "🌳", "2 hours back in your hands",      120),
        Level(3, "Tree",        "🌲", "8 hours of intentional living",   480),
        Level(4, "Summit",      "🏔️", "A full day of clarity",           1440),
        Level(5, "Flow",        "🌊", "Three days of momentum",          4320),
        Level(6, "Stellar",     "⭐", "A week of mindful presence",      10080),
        Level(7, "Enlightened", "🌌", "A month of conscious living",     43200)
    )

    fun computeLevel(totalMinutes: Long): Level =
        LEVELS.lastOrNull { totalMinutes >= it.minMinutes } ?: LEVELS.first()

    fun nextLevel(current: Level): Level? =
        LEVELS.getOrNull(current.index + 1)

    fun progressToNext(totalMinutes: Long, current: Level, next: Level?): Float {
        if (next == null) return 1f
        val range = (next.minMinutes - current.minMinutes).toFloat()
        val done  = (totalMinutes - current.minMinutes).toFloat()
        return (done / range).coerceIn(0f, 1f)
    }

    fun computeBadges(totalMinutes: Long, lifetimeDeclines: Long): List<MilestoneBadge> = listOf(
        MilestoneBadge("first_breath", "First Breath",  "🌬️", "Said no for the first time",          lifetimeDeclines >= 1),
        MilestoneBadge("ten_strong",   "Ten Strong",    "🔟", "10 mindful declines",                  lifetimeDeclines >= 10),
        MilestoneBadge("hour_saved",   "Hour Saved",    "⏰", "Reclaimed a full hour",                totalMinutes >= 60),
        MilestoneBadge("day_saved",    "Day Saved",     "📅", "24 hours of time back",               totalMinutes >= 1440),
        MilestoneBadge("century",      "Century",       "💯", "100 moments of resistance",            lifetimeDeclines >= 100),
        MilestoneBadge("week_saved",   "Week Saved",    "🗓️", "A full week reclaimed",               totalMinutes >= 10080),
        MilestoneBadge("mindful_500",  "Mindful 500",   "🎯", "500 intentional choices",              lifetimeDeclines >= 500),
        MilestoneBadge("month_saved",  "Month Saved",   "🌙", "30 days of your life returned",       totalMinutes >= 43200)
    )
}

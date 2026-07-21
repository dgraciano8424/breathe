package com.dgraciano.breathe.data.repository

import javax.inject.Inject
import javax.inject.Singleton

data class MentalHealthTip(
    val title: String,
    val description: String,
    val icon: String // Simplified for now
)

@Singleton
class MentalHealthTipsRepository @Inject constructor() {
    private val tips = listOf(
        MentalHealthTip(
            "Ground Yourself",
            "Feel your feet on the floor. Notice 3 things you can see right now.",
            "ground"
        ),
        MentalHealthTip(
            "Water Break",
            "Take a slow sip of water. Feel it cool your throat.",
            "water"
        ),
        MentalHealthTip(
            "Soft Sight",
            "Look away from the screen. Find something green or natural to look at.",
            "eye"
        ),
        MentalHealthTip(
            "Gentle Movement",
            "Roll your shoulders back and down. Let out a long sigh.",
            "move"
        ),
        MentalHealthTip(
            "The 5-Minute Rule",
            "Wait 5 minutes before opening this app. You might find you don't need it.",
            "clock"
        ),
        MentalHealthTip(
            "Ocean Breath",
            "Imagine your breath is like the tide. Slow in, slow out.",
            "wave"
        )
    )

    private val activities = listOf(
        "Read one page of a physical book",
        "Step outside for 2 minutes",
        "Stretch your neck and back",
        "Write down one thing you're grateful for",
        "Doodle on a piece of paper",
        "Listen to one song you love"
    )

    fun getRandomTip(): MentalHealthTip = tips.random()
    
    fun getRandomActivity(): String = activities.random()
}

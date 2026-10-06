package com.dgraciano.breathe.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A choice committed before the host closes, awaiting atomic history delivery. */
@Entity(tableName = "pending_choices")
data class PendingChoice(
    @PrimaryKey val choiceId: String,
    val packageName: String,
    val appName: String,
    val timestamp: Long,
    val outcome: String,
    val reason: String? = null
) {
    fun event(minutes: Int) = InterventionEvent(
        packageName = packageName, appName = appName, timestamp = timestamp,
        outcome = outcome, reason = reason, minutesSaved = minutes, choiceId = choiceId
    )
}

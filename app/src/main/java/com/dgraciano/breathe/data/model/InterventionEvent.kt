package com.dgraciano.breathe.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Every query filters on timestamp, and several also on outcome/packageName. Without
// these indices each one is a full table scan that degrades as history accumulates.
@Entity(
    tableName = "intervention_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["packageName", "timestamp"]),
        Index(value = ["outcome", "timestamp"])
    ]
)
data class InterventionEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val outcome: String,
    val reason: String? = null,
    val minutesSaved: Int = 0
) {
    companion object {
        const val OUTCOME_DECLINED = "DECLINED"
        const val OUTCOME_OPENED = "OPENED"

        const val REASON_BORED = "BORED"
        const val REASON_HABIT = "HABIT"
        const val REASON_ESCAPING = "ESCAPING"
        const val REASON_CURIOUS = "CURIOUS"
    }
}

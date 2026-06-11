package com.dgraciano.breathe.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intervention_events")
data class InterventionEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val outcome: String,
    val reason: String? = null
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

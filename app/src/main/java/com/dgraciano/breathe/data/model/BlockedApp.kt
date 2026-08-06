package com.dgraciano.breathe.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val addedAt: Long = System.currentTimeMillis(),
    /** How long the breathing pause holds before this app can be opened. */
    val pauseSeconds: Int = DEFAULT_PAUSE_SECONDS
) {
    companion object {
        const val DEFAULT_PAUSE_SECONDS = 15

        /** Offered in the picker; keep ascending so the UI can step through them. */
        val PAUSE_OPTIONS = listOf(5, 10, 15, 30, 60)
    }
}

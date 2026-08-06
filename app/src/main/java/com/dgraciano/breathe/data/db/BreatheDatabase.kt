package com.dgraciano.breathe.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.model.Quote

@Database(
    entities = [BlockedApp::class, Quote::class, InterventionEvent::class],
    version = 4,
    exportSchema = false
)
abstract class BreatheDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun quoteDao(): QuoteDao
    abstract fun interventionEventDao(): InterventionEventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS intervention_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        outcome TEXT NOT NULL,
                        reason TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE intervention_events ADD COLUMN minutesSaved INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Existing rows inherit the default, so pauses are unchanged until the
                // user picks a duration for that app.
                database.execSQL(
                    "ALTER TABLE blocked_apps ADD COLUMN pauseSeconds INTEGER NOT NULL " +
                            "DEFAULT ${BlockedApp.DEFAULT_PAUSE_SECONDS}"
                )
            }
        }
    }
}

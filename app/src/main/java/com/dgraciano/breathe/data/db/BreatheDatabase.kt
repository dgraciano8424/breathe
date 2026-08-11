package com.dgraciano.breathe.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.model.InterventionEvent

@Database(
    entities = [BlockedApp::class, InterventionEvent::class],
    version = 5,
    exportSchema = true
)
abstract class BreatheDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
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

        /**
         * Index names must match Room's generated convention exactly
         * (index_<table>_<col>[_<col>...]) or the schema validation on open will fail.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_intervention_events_timestamp " +
                        "ON intervention_events (timestamp)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_intervention_events_packageName_timestamp " +
                        "ON intervention_events (packageName, timestamp)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_intervention_events_outcome_timestamp " +
                        "ON intervention_events (outcome, timestamp)"
                )
            }
        }

        /**
         * Drops the quote cache. The quote feature was never rendered — the value fed an
         * unused parameter — so the table, the API client and the INTERNET permission
         * were all removed rather than shipped as an unused review surface.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS quotes")
            }
        }

        val ALL_MIGRATIONS =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}

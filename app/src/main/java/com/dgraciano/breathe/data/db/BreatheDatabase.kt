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
    version = 2,
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
    }
}

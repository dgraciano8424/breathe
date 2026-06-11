package com.dgraciano.breathe.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.model.Quote

@Database(entities = [BlockedApp::class, Quote::class], version = 1, exportSchema = false)
abstract class BreatheDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun quoteDao(): QuoteDao
}

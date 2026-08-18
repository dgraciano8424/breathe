package com.dgraciano.breathe.data.db

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BreatheDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BreatheDatabase::class.java,
    )

    @Test
    fun migrationOneToTwoPreservesBlockedAppsAndQuotes() {
        migrateAndAssert(1, 2, BreatheDatabase.MIGRATION_1_2)
    }

    @Test
    fun migrationTwoToThreePreservesEventsAndDefaultsMinutesSaved() {
        migrateAndAssert(2, 3, BreatheDatabase.MIGRATION_2_3)
    }

    @Test
    fun migrationThreeToFourPreservesRowsAndDefaultsPauseSeconds() {
        migrateAndAssert(3, 4, BreatheDatabase.MIGRATION_3_4)
    }

    @Test
    fun migrationFourToFiveDropsOnlyTheUnusedQuoteCache() {
        migrateAndAssert(4, 5, BreatheDatabase.MIGRATION_4_5)
    }

    @Test
    fun migrationFiveToSixPreservesRowsAndAddsAggregateIndexes() {
        migrateAndAssert(5, 6, BreatheDatabase.MIGRATION_5_6)
    }

    @Test
    fun migrationOneToSixPreservesUserDataAcrossTheCompleteHistory() {
        migrateAndAssert(1, 6, *ALL_MIGRATIONS)
    }

    @Test
    fun migrationTwoToSixPreservesUserDataAcrossTheCompleteHistory() {
        migrateAndAssert(2, 6, *ALL_MIGRATIONS.drop(1).toTypedArray())
    }

    @Test
    fun migrationThreeToSixPreservesUserDataAcrossTheCompleteHistory() {
        migrateAndAssert(3, 6, *ALL_MIGRATIONS.drop(2).toTypedArray())
    }

    @Test
    fun migrationFourToSixPreservesCustomPauseAndDropsOnlyQuotes() {
        migrateAndAssert(4, 6, *ALL_MIGRATIONS.drop(3).toTypedArray())
    }

    private fun migrateAndAssert(from: Int, to: Int, vararg migrations: Migration) {
        val databaseName = "migration-$from-$to.db"
        helper.createDatabase(databaseName, from).use { database ->
            seedHistoricalRows(database, from)
        }

        helper.runMigrationsAndValidate(databaseName, to, true, *migrations).use { database ->
            assertHistoricalRows(database, from, to)
        }
    }

    private fun seedHistoricalRows(database: SupportSQLiteDatabase, version: Int) {
        val blockedColumns = if (version >= 4) {
            "packageName, appName, addedAt, pauseSeconds"
        } else {
            "packageName, appName, addedAt"
        }
        val blockedValues = if (version >= 4) {
            "'com.example.monitored', 'Monitored', 101, 30"
        } else {
            "'com.example.monitored', 'Monitored', 101"
        }
        database.execSQL("INSERT INTO blocked_apps ($blockedColumns) VALUES ($blockedValues)")

        if (version <= 4) {
            database.execSQL(
                "INSERT INTO quotes (id, text, author) " +
                    "VALUES (41, 'Unused cached quote', 'Historical Author')"
            )
        }

        if (version >= 2) {
            val eventColumns = if (version >= 3) {
                "id, packageName, appName, timestamp, outcome, reason, minutesSaved"
            } else {
                "id, packageName, appName, timestamp, outcome, reason"
            }
            val eventValues = if (version >= 3) {
                "73, 'com.example.monitored', 'Monitored', 202, 'DECLINED', 'HABIT', 7"
            } else {
                "73, 'com.example.monitored', 'Monitored', 202, 'DECLINED', 'HABIT'"
            }
            database.execSQL("INSERT INTO intervention_events ($eventColumns) VALUES ($eventValues)")
        }
    }

    private fun assertHistoricalRows(database: SupportSQLiteDatabase, from: Int, to: Int) {
        database.query("SELECT * FROM blocked_apps WHERE packageName = 'com.example.monitored'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Monitored", cursor.getString(cursor.getColumnIndexOrThrow("appName")))
                assertEquals(101L, cursor.getLong(cursor.getColumnIndexOrThrow("addedAt")))
                if (to >= 4) {
                    val expectedPauseSeconds = if (from >= 4) 30 else 15
                    assertEquals(
                        expectedPauseSeconds,
                        cursor.getInt(cursor.getColumnIndexOrThrow("pauseSeconds")),
                    )
                }
            }

        if (from >= 2) {
            database.query("SELECT * FROM intervention_events WHERE id = 73").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    "com.example.monitored",
                    cursor.getString(cursor.getColumnIndexOrThrow("packageName")),
                )
                assertEquals(202L, cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")))
                assertEquals("DECLINED", cursor.getString(cursor.getColumnIndexOrThrow("outcome")))
                assertEquals("HABIT", cursor.getString(cursor.getColumnIndexOrThrow("reason")))
                if (to >= 3) {
                    val expectedMinutesSaved = if (from >= 3) 7 else 0
                    assertEquals(
                        expectedMinutesSaved,
                        cursor.getInt(cursor.getColumnIndexOrThrow("minutesSaved")),
                    )
                }
            }
        } else if (to >= 2) {
            database.query("SELECT COUNT(*) FROM intervention_events").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }

        if (to <= 4) {
            database.query("SELECT text, author FROM quotes WHERE id = 41").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Unused cached quote", cursor.getString(0))
                assertEquals("Historical Author", cursor.getString(1))
            }
        } else {
            assertFalse(database.hasTable("quotes"))
        }

        if (to == 6) {
            val indexNames = buildSet {
                database.query("PRAGMA index_list(`intervention_events`)").use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
            }
            assertTrue("index_intervention_events_timestamp" in indexNames)
            assertTrue("index_intervention_events_outcome_timestamp" in indexNames)
        }
    }

    private fun SupportSQLiteDatabase.hasTable(name: String): Boolean =
        query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name))
            .use { it.moveToFirst() }

    private companion object {
        val ALL_MIGRATIONS = arrayOf(
            BreatheDatabase.MIGRATION_1_2,
            BreatheDatabase.MIGRATION_2_3,
            BreatheDatabase.MIGRATION_3_4,
            BreatheDatabase.MIGRATION_4_5,
            BreatheDatabase.MIGRATION_5_6,
        )
    }
}

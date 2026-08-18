package com.dgraciano.breathe.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dgraciano.breathe.data.model.InterventionEvent
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterventionAggregateDaoTest {

    private lateinit var database: BreatheDatabase
    private lateinit var dao: InterventionEventDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BreatheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.interventionEventDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun todayAggregateInvalidatesForExactMidnightInsertAndEnrichment() = runBlocking {
        val midnight = ZonedDateTime.of(
            2026, 8, 18, 0, 0, 0, 0, ZoneId.of("America/Los_Angeles")
        ).toInstant().toEpochMilli()
        val emissions = Channel<TodayAggregate>(Channel.UNLIMITED)
        val lifetimeEmissions = Channel<LifetimeAggregate>(Channel.UNLIMITED)
        val collection = launch(Dispatchers.Default) {
            dao.observeTodayAggregate(midnight).collect(emissions::send)
        }
        val lifetimeCollection = launch(Dispatchers.Default) {
            dao.observeLifetimeAggregate().collect(lifetimeEmissions::send)
        }

        assertEquals(TodayAggregate(0, 0, 0), withTimeout(5_000) { emissions.receive() })
        assertEquals(LifetimeAggregate(0, 0), withTimeout(5_000) { lifetimeEmissions.receive() })
        val id = dao.insert(
            InterventionEvent(
                packageName = "com.example",
                appName = "Example",
                timestamp = midnight,
                outcome = InterventionEvent.OUTCOME_DECLINED,
            )
        )
        assertEquals(TodayAggregate(1, 1, 0), withTimeout(5_000) { emissions.receive() })
        assertEquals(LifetimeAggregate(1, 0), withTimeout(5_000) { lifetimeEmissions.receive() })
        assertEquals(1, dao.getAttemptCount("com.example", midnight))
        assertEquals(1, dao.getTotalAttempts(midnight))
        assertEquals(1, dao.getTotalDeclined(midnight))
        assertEquals(0, dao.getTotalMinutesSavedSince(midnight))
        assertEquals(1, dao.getTopApps(midnight).single().count)

        dao.updateMinutesSaved(id, 14)
        assertEquals(TodayAggregate(1, 1, 14), withTimeout(5_000) { emissions.receive() })
        assertEquals(LifetimeAggregate(1, 14), withTimeout(5_000) { lifetimeEmissions.receive() })
        collection.cancel()
        lifetimeCollection.cancel()
    }

    @Test
    fun aggregateSchemaHasTimestampAndOutcomeTimestampIndexes() {
        val names = buildSet {
            database.openHelper.readableDatabase
                .query("PRAGMA index_list(`intervention_events`)")
                .use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
        }

        assertTrue("index_intervention_events_timestamp" in names)
        assertTrue("index_intervention_events_outcome_timestamp" in names)
    }

    @Test
    fun migrationFiveToSixAddsAggregateIndexes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "aggregate-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS intervention_events (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                packageName TEXT NOT NULL,
                                appName TEXT NOT NULL,
                                timestamp INTEGER NOT NULL,
                                outcome TEXT NOT NULL,
                                reason TEXT,
                                minutesSaved INTEGER NOT NULL DEFAULT 0
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )

        try {
            val sqlite = helper.writableDatabase
            BreatheDatabase.MIGRATION_5_6.migrate(sqlite)
            val names = buildSet {
                sqlite.query("PRAGMA index_list(`intervention_events`)").use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
            }
            assertTrue("index_intervention_events_timestamp" in names)
            assertTrue("index_intervention_events_outcome_timestamp" in names)
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }
}

package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.InterventionEventDao
import com.dgraciano.breathe.data.model.InterventionEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.time.ZonedDateTime

class StatsRepositoryTest {

    @Test
    fun `week starts at the preceding Monday for every locale and day`() {
        for (locale in listOf(java.util.Locale.US, java.util.Locale.FRANCE)) {
            for (day in 5..11) {
                val now = Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Los_Angeles"), locale).apply {
                    clear(); set(2026, Calendar.OCTOBER, day, 13, 30)
                }
                val result = now.clone() as Calendar
                result.timeInMillis = mondayStart(now)
                assertEquals(5, result.get(Calendar.DAY_OF_MONTH))
                assertEquals(Calendar.MONDAY, result.get(Calendar.DAY_OF_WEEK))
                assertEquals(0, result.get(Calendar.HOUR_OF_DAY))
                assertEquals(day, now.get(Calendar.DAY_OF_MONTH))
            }
        }
    }

    private lateinit var dao: InterventionEventDao
    private lateinit var repo: StatsRepository

    @Before
    fun setUp() {
        dao = mockk()
        repo = StatsRepository(dao)
    }

    @Test
    fun `getTodayAttemptCount passes midnight of today as since`() = runTest {
        val slot = slot<Long>()
        coEvery { dao.getAttemptCount(any(), capture(slot)) } returns 3

        repo.getTodayAttemptCount("com.example.app")

        val captured = slot.captured
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = captured }
        // since must be within the last 24 hours and at midnight
        assert(now - captured < TimeUnit.HOURS.toMillis(24)) { "since should be within last 24h" }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `getTodayTotalAttempts passes midnight of today as since`() = runTest {
        val slot = slot<Long>()
        coEvery { dao.getTotalAttempts(capture(slot)) } returns 5

        repo.getTodayTotalAttempts()

        val cal = Calendar.getInstance().apply { timeInMillis = slot.captured }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
    }

    @Test
    fun `getTodayDeclined passes midnight of today as since`() = runTest {
        val slot = slot<Long>()
        coEvery { dao.getTotalDeclined(capture(slot)) } returns 2

        repo.getTodayDeclined()

        val cal = Calendar.getInstance().apply { timeInMillis = slot.captured }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `getWeeklyTotalAttempts passes Monday midnight as since`() = runTest {
        val slot = slot<Long>()
        coEvery { dao.getTotalAttempts(capture(slot)) } returns 10

        repo.getWeeklyTotalAttempts()

        val cal = Calendar.getInstance().apply { timeInMillis = slot.captured }
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `Sunday weekly totals use the preceding Monday`() = runTest {
        val captured = slot<Long>()
        coEvery { dao.getTotalAttempts(capture(captured)) } returns 10
        repo.clock = { ZonedDateTime.parse("2026-10-04T18:00:00-07:00[America/Los_Angeles]") }
        repo.getWeeklyTotalAttempts()
        assertEquals(
            ZonedDateTime.parse("2026-09-28T00:00:00-07:00[America/Los_Angeles]").toInstant().toEpochMilli(),
            captured.captured
        )
    }

    @Test
    fun `Monday totals begin at the current Monday rather than the prior week`() = runTest {
        val captured = slot<Long>()
        coEvery { dao.getTotalAttempts(capture(captured)) } returns 10
        repo.clock = { ZonedDateTime.parse("2026-10-05T18:00:00-07:00[America/Los_Angeles]") }
        repo.getWeeklyTotalAttempts()
        assertEquals(
            ZonedDateTime.parse("2026-10-05T00:00:00-07:00[America/Los_Angeles]").toInstant().toEpochMilli(),
            captured.captured
        )
    }

    @Test
    fun `daily boundary uses local midnight on a daylight saving transition`() = runTest {
        val captured = slot<Long>()
        coEvery { dao.getTotalAttempts(capture(captured)) } returns 10
        repo.clock = { ZonedDateTime.parse("2026-11-01T18:00:00-08:00[America/Los_Angeles]") }
        repo.getTodayTotalAttempts()
        assertEquals(
            ZonedDateTime.parse("2026-11-01T00:00:00-07:00[America/Los_Angeles]").toInstant().toEpochMilli(),
            captured.captured
        )
    }

    @Test
    fun `getWeeklyDeclined passes Monday midnight as since`() = runTest {
        val slot = slot<Long>()
        coEvery { dao.getTotalDeclined(capture(slot)) } returns 4

        repo.getWeeklyDeclined()

        val cal = Calendar.getInstance().apply { timeInMillis = slot.captured }
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `getTopAppsThisWeek passes Monday midnight as since`() = runTest {
        val slot = slot<Long>()
        coEvery { dao.getTopApps(capture(slot)) } returns emptyList()

        repo.getTopAppsThisWeek()

        val cal = Calendar.getInstance().apply { timeInMillis = slot.captured }
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `recordEvent delegates to dao insert`() = runTest {
        val event = InterventionEvent(
            packageName = "com.example",
            appName = "Example",
            outcome = InterventionEvent.OUTCOME_DECLINED
        )
        coEvery { dao.insert(event) } returns Unit

        repo.recordEvent(event)

        coVerify(exactly = 1) { dao.insert(event) }
    }
}

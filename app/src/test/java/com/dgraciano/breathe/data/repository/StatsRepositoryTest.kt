package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.InterventionEventDao
import com.dgraciano.breathe.data.db.LifetimeAggregate
import com.dgraciano.breathe.data.db.TodayAggregate
import com.dgraciano.breathe.data.model.InterventionEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class StatsRepositoryTest {

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
        coEvery { dao.insert(event) } returns 1L

        repo.recordEvent(event)

        coVerify(exactly = 1) { dao.insert(event) }
    }

    @Test
    fun `today aggregate observes the committed Room projection since midnight`() = runTest {
        val since = slot<Long>()
        val aggregate = TodayAggregate(attempts = 4, declined = 3, minutesSaved = 27)
        every { dao.observeTodayAggregate(capture(since)) } returns flowOf(aggregate)

        assertEquals(aggregate, repo.observeTodayAggregate().first())

        val calendar = Calendar.getInstance().apply { timeInMillis = since.captured }
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.SECOND))
        assertEquals(0, calendar.get(Calendar.MILLISECOND))
    }

    @Test
    fun `lifetime aggregate exposes the single committed Room projection`() = runTest {
        val aggregate = LifetimeAggregate(declined = 18, minutesSaved = 135)
        every { dao.observeLifetimeAggregate() } returns flowOf(aggregate)

        assertEquals(aggregate, repo.observeLifetimeAggregate().first())
        verify(exactly = 1) { dao.observeLifetimeAggregate() }
    }
}

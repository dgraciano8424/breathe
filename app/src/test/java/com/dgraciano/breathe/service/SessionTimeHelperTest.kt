package com.dgraciano.breathe.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SessionTimeHelperTest {

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var helper: SessionTimeHelper
    private var fakeNow = 1_000_000_000L

    @Before
    fun setUp() {
        usageStatsManager = mockk()
        helper = SessionTimeHelper(usageStatsManager)
        helper.clock = { fakeNow }
        // The helper constructs its own UsageEvents.Event and reads it after each
        // getNextEvent() call, so the constructed instance has to be intercepted.
        mockkConstructor(UsageEvents.Event::class)
    }

    @After
    fun tearDown() {
        unmockkConstructor(UsageEvents.Event::class)
    }

    /**
     * Events carry real epoch timestamps, so fixtures offset from a plausible instant
     * rather than from zero — the production code treats a zero timestamp as "no
     * session in progress".
     */
    private val baseTime = 900_000_000L

    /** One foreground/background pair for [pkg] lasting [minutes]. */
    private fun session(pkg: String, minutes: Int, startAt: Long = 0L) = listOf(
        Event(UsageEvents.Event.MOVE_TO_FOREGROUND, pkg, baseTime + startAt),
        Event(UsageEvents.Event.MOVE_TO_BACKGROUND, pkg, baseTime + startAt + minutes * 60_000L)
    )

    private data class Event(val type: Int, val pkg: String, val timeStamp: Long)

    /** The event the shared UsageEvents.Event instance currently reports. */
    private var current: Event? = null

    /**
     * Scripts consecutive queryEvents() calls: the first call sees the first batch, the
     * second the second, and so on. The last batch repeats for any further calls.
     *
     * Property reads resolve against whichever event getNextEvent() last advanced to,
     * mirroring the real cursor API. Queueing each property independently would desync
     * the moment the code under test reads one of them a different number of times.
     */
    private fun stubQueries(vararg batches: List<Event>) {
        val usageEvents = batches.map { batch ->
            var i = 0
            mockk<UsageEvents>().also {
                every { it.hasNextEvent() } answers { i < batch.size }
                every { it.getNextEvent(any()) } answers { current = batch[i++]; true }
            }
        }
        every { anyConstructed<UsageEvents.Event>().packageName } answers { current!!.pkg }
        every { anyConstructed<UsageEvents.Event>().eventType } answers { current!!.type }
        every { anyConstructed<UsageEvents.Event>().timeStamp } answers { current!!.timeStamp }
        every { usageStatsManager.queryEvents(any(), any()) } returnsMany usageEvents
    }

    @Test
    fun `averages completed sessions for the requested package`() {
        stubQueries(session("com.social", 10) + session("com.social", 20, startAt = 60 * 60_000L))

        assertEquals(15, helper.getAvgSessionMinutes("com.social"))
    }

    @Test
    fun `falls back to the default when no sessions are recorded`() {
        stubQueries(emptyList())

        assertEquals(20, helper.getAvgSessionMinutes("com.social"))
    }

    @Test
    fun `ignores sessions shorter than the sanity window`() {
        // 2s of foreground time is a bounce, not a session.
        val bounce = listOf(
            Event(UsageEvents.Event.MOVE_TO_FOREGROUND, "com.social", baseTime),
            Event(UsageEvents.Event.MOVE_TO_BACKGROUND, "com.social", baseTime + 2_000L)
        )
        stubQueries(bounce)

        assertEquals(20, helper.getAvgSessionMinutes("com.social"))
    }

    @Test
    fun `serves a cached average within the TTL without re-querying`() {
        stubQueries(session("com.social", 10))

        assertEquals(10, helper.getAvgSessionMinutes("com.social"))
        fakeNow += TimeUnit.HOURS.toMillis(1)
        assertEquals(10, helper.getAvgSessionMinutes("com.social"))

        verify(exactly = 1) { usageStatsManager.queryEvents(any(), any()) }
    }

    @Test
    fun `recomputes once the cached average goes stale`() {
        stubQueries(session("com.social", 10), session("com.social", 30))

        assertEquals(10, helper.getAvgSessionMinutes("com.social"))
        // Habits changed; after the TTL the figure must not stay frozen.
        fakeNow += TimeUnit.HOURS.toMillis(7)

        assertEquals(30, helper.getAvgSessionMinutes("com.social"))
        verify(exactly = 2) { usageStatsManager.queryEvents(any(), any()) }
    }

    @Test
    fun `caches per package rather than globally`() {
        stubQueries(session("com.social", 10), session("com.news", 30))

        assertEquals(10, helper.getAvgSessionMinutes("com.social"))
        assertEquals(30, helper.getAvgSessionMinutes("com.news"))
    }

    @Test
    fun `bounds the query window to about a week`() {
        val start = slot<Long>()
        val usageEvents = mockk<UsageEvents> { every { hasNextEvent() } returns false }
        every { usageStatsManager.queryEvents(capture(start), any()) } returns usageEvents

        helper.getAvgSessionMinutes("com.social")

        val lookback = fakeNow - start.captured
        assertEquals(TimeUnit.DAYS.toMillis(7), lookback)
    }

    @Test
    fun `averages only the newest sessions when history is long`() {
        // 60 sessions: the oldest 10 are 1 minute, the newest 50 are 30 minutes.
        // Only the newest 50 should count, so the average is exactly 30.
        val events = mutableListOf<Event>()
        var at = 0L
        repeat(10) {
            events += session("com.social", 1, startAt = at)
            at += TimeUnit.HOURS.toMillis(1)
        }
        repeat(50) {
            events += session("com.social", 30, startAt = at)
            at += TimeUnit.HOURS.toMillis(2)
        }
        stubQueries(events)

        assertEquals(30, helper.getAvgSessionMinutes("com.social"))
    }

    @Test
    fun `ignores events belonging to other packages`() {
        stubQueries(session("com.other", 60) + session("com.social", 10))

        assertEquals(10, helper.getAvgSessionMinutes("com.social"))
    }

    @Test
    fun `rounds a sub-minute average up to one minute`() {
        // 30s is above the 5s sanity floor but rounds to 0 minutes.
        val short = listOf(
            Event(UsageEvents.Event.MOVE_TO_FOREGROUND, "com.social", baseTime),
            Event(UsageEvents.Event.MOVE_TO_BACKGROUND, "com.social", baseTime + 30_000L)
        )
        stubQueries(short)

        assertTrue(helper.getAvgSessionMinutes("com.social") >= 1)
    }
}

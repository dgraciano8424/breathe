package com.dgraciano.breathe.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkConstructor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ForegroundAppDetectorTest {

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var detector: ForegroundAppDetector
    private var fakeNow = 1_000_000_000L

    @Before
    fun setUp() {
        usageStatsManager = mockk()
        detector = ForegroundAppDetector(usageStatsManager)
        detector.clock = { fakeNow }
        // The detector constructs its own UsageEvents.Event and reads it after each
        // getNextEvent() call, so the constructed instance has to be intercepted.
        mockkConstructor(UsageEvents.Event::class)
    }

    @After
    fun tearDown() {
        unmockkConstructor(UsageEvents.Event::class)
    }

    /**
     * Feeds the detector a sequence of (eventType, packageName) pairs, mimicking the
     * cursor-style UsageEvents API: hasNextEvent() is true once per event, and the
     * shared Event instance reports each pair in turn.
     */
    private fun stubEvents(vararg events: Pair<Int, String>) {
        stubQueries(events.toList())
    }

    /**
     * Scripts consecutive queryEvents() calls: the first call sees the first batch, the
     * second call the second, and so on. The last batch repeats for any further calls.
     */
    private fun stubQueries(vararg batches: List<Pair<Int, String>>) {
        val usageEvents = batches.map { batch ->
            mockk<UsageEvents>().also {
                every { it.hasNextEvent() } returnsMany (List(batch.size) { true } + false)
                every { it.getNextEvent(any()) } returns true
            }
        }
        val all = batches.flatMap { it }
        if (all.isNotEmpty()) {
            every { anyConstructed<UsageEvents.Event>().eventType } returnsMany all.map { it.first }
        }
        // packageName is only read for events the detector considers foreground events,
        // so only those may be queued up here.
        val foregroundPackages = all.filter { isForeground(it.first) }.map { it.second }
        if (foregroundPackages.isNotEmpty()) {
            every { anyConstructed<UsageEvents.Event>().packageName } returnsMany foregroundPackages
        }
        every { usageStatsManager.queryEvents(any(), any()) } returnsMany usageEvents
    }

    private fun isForeground(eventType: Int) =
        eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
            eventType == UsageEvents.Event.MOVE_TO_FOREGROUND

    @Test
    fun `returns package of the most recent foreground event`() {
        stubEvents(
            UsageEvents.Event.ACTIVITY_RESUMED to "com.old.app",
            UsageEvents.Event.ACTIVITY_RESUMED to "com.current.app"
        )

        assertEquals("com.current.app", detector.getCurrentApp())
    }

    @Test
    fun `returns null when no events are reported`() {
        stubEvents()

        assertNull(detector.getCurrentApp())
    }

    @Test
    fun `returns single app when only one foreground event`() {
        stubEvents(UsageEvents.Event.ACTIVITY_RESUMED to "com.only.app")

        assertEquals("com.only.app", detector.getCurrentApp())
    }

    @Test
    fun `returns last foreground app when three events present`() {
        stubEvents(
            UsageEvents.Event.ACTIVITY_RESUMED to "com.first.app",
            UsageEvents.Event.ACTIVITY_RESUMED to "com.second.app",
            UsageEvents.Event.ACTIVITY_RESUMED to "com.newest.app"
        )

        assertEquals("com.newest.app", detector.getCurrentApp())
    }

    @Test
    fun `ignores non-foreground events`() {
        stubEvents(
            UsageEvents.Event.ACTIVITY_RESUMED to "com.foreground.app",
            UsageEvents.Event.ACTIVITY_PAUSED to "com.backgrounded.app"
        )

        assertEquals("com.foreground.app", detector.getCurrentApp())
    }

    @Test
    fun `returns null when only non-foreground events are reported`() {
        stubEvents(
            UsageEvents.Event.ACTIVITY_PAUSED to "com.backgrounded.app",
            UsageEvents.Event.ACTIVITY_STOPPED to "com.stopped.app"
        )

        assertNull(detector.getCurrentApp())
    }

    @Test
    fun `keeps reporting the last known app when no new events arrive`() {
        stubQueries(
            listOf(UsageEvents.Event.ACTIVITY_RESUMED to "com.social.app"),
            emptyList(),
            emptyList()
        )

        assertEquals("com.social.app", detector.getCurrentApp())
        // The user sits in the app; no further transitions are reported.
        fakeNow += 30_000
        assertEquals("com.social.app", detector.getCurrentApp())
        fakeNow += 5 * 60_000
        assertEquals("com.social.app", detector.getCurrentApp())
    }

    @Test
    fun `switches to the new app once a newer foreground event arrives`() {
        stubQueries(
            listOf(UsageEvents.Event.ACTIVITY_RESUMED to "com.social.app"),
            emptyList(),
            listOf(UsageEvents.Event.ACTIVITY_RESUMED to "com.other.app")
        )

        assertEquals("com.social.app", detector.getCurrentApp())
        fakeNow += 1_000
        assertEquals("com.social.app", detector.getCurrentApp())
        fakeNow += 1_000
        assertEquals("com.other.app", detector.getCurrentApp())
    }

    @Test
    fun `scans a wide window on cold start so an already-open app is recovered`() {
        val start = slot<Long>()
        val usageEvents = mockk<UsageEvents> {
            every { hasNextEvent() } returns false
        }
        every { usageStatsManager.queryEvents(capture(start), any()) } returns usageEvents

        detector.getCurrentApp()

        val lookback = fakeNow - start.captured
        assertTrue(
            "expected a lookback of at least an hour, was ${lookback}ms",
            lookback >= TimeUnit.HOURS.toMillis(1)
        )
    }

    @Test
    fun `narrows the window to incremental scans once an event has been seen`() {
        val starts = mutableListOf<Long>()
        val first = mockk<UsageEvents> {
            every { hasNextEvent() } returnsMany listOf(true, false)
            every { getNextEvent(any()) } returns true
        }
        val second = mockk<UsageEvents> { every { hasNextEvent() } returns false }
        every { anyConstructed<UsageEvents.Event>().eventType } returns
            UsageEvents.Event.ACTIVITY_RESUMED
        every { anyConstructed<UsageEvents.Event>().packageName } returns "com.social.app"
        every { usageStatsManager.queryEvents(capture(starts), any()) } returnsMany
            listOf(first, second)

        detector.getCurrentApp()
        fakeNow += 500
        detector.getCurrentApp()

        assertEquals(2, starts.size)
        // Second scan covers only the gap since the first, not another 24 hours.
        assertTrue(
            "second scan should be incremental, looked back ${fakeNow - starts[1]}ms",
            fakeNow - starts[1] < TimeUnit.MINUTES.toMillis(1)
        )
    }

    @Test
    fun `recovers after a permission grant that follows an empty first scan`() {
        stubQueries(
            emptyList(),
            listOf(UsageEvents.Event.ACTIVITY_RESUMED to "com.social.app")
        )

        // Without usage access the first scan sees nothing.
        assertNull(detector.getCurrentApp())
        // Permission granted; the throttle must have expired before the next wide scan.
        fakeNow += 6_000
        assertEquals("com.social.app", detector.getCurrentApp())
    }

    @Test
    fun `throttles repeated wide scans while no event has ever been seen`() {
        var queries = 0
        val usageEvents = mockk<UsageEvents> { every { hasNextEvent() } returns false }
        every { usageStatsManager.queryEvents(any(), any()) } answers {
            queries++
            usageEvents
        }

        detector.getCurrentApp()
        // The service polls twice a second; these must not each trigger a 24h scan.
        repeat(4) {
            fakeNow += 500
            detector.getCurrentApp()
        }

        assertEquals(1, queries)
    }

    @Test
    fun `reset clears the remembered app`() {
        stubQueries(
            listOf(UsageEvents.Event.ACTIVITY_RESUMED to "com.social.app"),
            emptyList()
        )

        assertEquals("com.social.app", detector.getCurrentApp())
        detector.reset()
        fakeNow += 1_000
        assertNull(detector.getCurrentApp())
    }
}

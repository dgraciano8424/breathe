package com.dgraciano.breathe.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ForegroundAppDetectorTest {

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var detector: ForegroundAppDetector

    @Before
    fun setUp() {
        usageStatsManager = mockk()
        detector = ForegroundAppDetector(usageStatsManager)
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
        val usageEvents = mockk<UsageEvents>()
        every { usageEvents.hasNextEvent() } returnsMany (List(events.size) { true } + false)
        every { usageEvents.getNextEvent(any()) } returns true
        if (events.isNotEmpty()) {
            every { anyConstructed<UsageEvents.Event>().eventType } returnsMany events.map { it.first }
        }
        // packageName is only read for events the detector considers foreground events,
        // so only those may be queued up here.
        val foregroundPackages = events.filter { isForeground(it.first) }.map { it.second }
        if (foregroundPackages.isNotEmpty()) {
            every { anyConstructed<UsageEvents.Event>().packageName } returnsMany foregroundPackages
        }
        every { usageStatsManager.queryEvents(any(), any()) } returns usageEvents
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
}

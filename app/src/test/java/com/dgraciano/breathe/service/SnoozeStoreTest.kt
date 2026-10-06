package com.dgraciano.breathe.service

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnoozeStoreTest {
    private var disk = 0L
    private var memory = 0L
    private var failWrites = false
    private val editor = mockk<SharedPreferences.Editor>()
    private val preferences = mockk<SharedPreferences>()
    init {
        every { preferences.getLong("until", 0) } answers { memory }
        every { preferences.edit() } returns editor
        every { editor.putLong("until", any()) } answers { memory = secondArg(); editor }
        every { editor.commit() } answers { if (failWrites) false else { disk = memory; true } }
    }

    @Test fun `supported durations persist and survive a new store`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = SnoozeStore(preferences, dispatcher) { 1_000L }
        for (minutes in listOf(15, 30, 60)) {
            store.snooze(minutes)
            assertEquals(1_000L + minutes * 60_000L, disk)
            assertEquals(disk, SnoozeStore(preferences, dispatcher) { 1_000L }.currentDeadline)
        }
    }

    @Test fun `deadline expires for observers and event handling without saving again`() = runTest {
        val store = SnoozeStore(preferences, StandardTestDispatcher(testScheduler)) { 1_000L + testScheduler.currentTime }
        val deadlines = mutableListOf<Long>()
        backgroundScope.launch { store.deadline.collect { deadlines.add(it) } }
        runCurrent()
        store.snooze(15); runCurrent()
        advanceTimeBy(899_999); runCurrent()
        assertTrue(store.isSnoozed())
        advanceTimeBy(1); runCurrent()
        assertFalse(store.isSnoozed())
        assertEquals(listOf(0L, 901_000L, 0L), deadlines)
        assertEquals(901_000L, disk)
    }

    @Test fun `resume persists zero and cancels an older expiry`() = runTest {
        val store = SnoozeStore(preferences, StandardTestDispatcher(testScheduler)) { 1_000L + testScheduler.currentTime }
        val deadlines = mutableListOf<Long>()
        backgroundScope.launch { store.deadline.collect { deadlines.add(it) } }
        store.snooze(15); runCurrent()
        store.resume(); runCurrent()
        store.snooze(60); runCurrent()
        advanceTimeBy(900_000); runCurrent()
        assertTrue(store.isSnoozed())
        assertEquals(3_601_000L, deadlines.last())
        store.resume(); runCurrent()
        assertEquals(0L, disk)
        assertEquals(0L, deadlines.last())
    }

    @Test fun `failed snooze leaves the previous deadline and preference memory intact`() = runTest {
        val store = SnoozeStore(preferences, StandardTestDispatcher(testScheduler)) { 1_000L }
        failWrites = true
        try { store.snooze(15); fail("Expected storage failure") } catch (_: IOException) { }
        assertEquals(0L, store.currentDeadline)
        assertEquals(0L, memory)
        assertEquals(0L, disk)
        failWrites = false
        store.snooze(15)
        assertTrue(store.isSnoozed())
    }

    @Test fun `failed resume keeps an active snooze`() = runTest {
        val store = SnoozeStore(preferences, StandardTestDispatcher(testScheduler)) { 1_000L }
        store.snooze(30)
        val before = disk
        failWrites = true
        try { store.resume(); fail("Expected storage failure") } catch (_: IOException) { }
        assertEquals(before, store.currentDeadline)
        assertEquals(before, memory)
        assertEquals(before, disk)
    }

    @Test fun `expired saved deadlines do not snooze after restart or device sleep`() = runTest {
        memory = 900L
        var now = 500L
        val store = SnoozeStore(preferences, StandardTestDispatcher(testScheduler)) { now }
        assertTrue(store.isSnoozed())
        now = 1_000L
        assertFalse(store.isSnoozed())
        assertEquals(0L, store.deadline.first())
        assertEquals(0L, SnoozeStore(preferences, StandardTestDispatcher(testScheduler)) { now }.currentDeadline)
    }

    @Test fun `invalid durations cannot change the saved setting`() = runTest {
        val store = SnoozeStore(preferences, StandardTestDispatcher(testScheduler)) { 1_000L }
        for (minutes in listOf(-1, 0, 1, 120, Int.MAX_VALUE)) {
            try { store.snooze(minutes); fail("Expected invalid duration") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0L, disk)
        assertFalse(store.isSnoozed())
    }
}

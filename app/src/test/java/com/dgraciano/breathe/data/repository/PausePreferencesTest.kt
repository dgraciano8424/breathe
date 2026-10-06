package com.dgraciano.breathe.data.repository

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PausePreferencesTest {
    private var memory = ""
    private var disk = ""
    private var failWrites = false
    private val editor = mockk<SharedPreferences.Editor>()
    private val preferences = mockk<SharedPreferences>()
    init {
        every { preferences.getString("reminder", "") } answers { memory }
        every { preferences.edit() } returns editor
        every { editor.putString("reminder", any()) } answers { memory = secondArg(); editor }
        every { editor.commit() } answers { if (failWrites) false else { disk = memory; true } }
    }

    @Test fun `a normalized reminder persists and survives recreation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = PausePreferences(preferences, dispatcher)
        store.saveReminder("  Take a\n short walk  ")
        assertEquals("Take a short walk", disk)
        assertEquals(disk, PausePreferences(preferences, dispatcher).reminder.value)
    }

    @Test fun `observers receive changes and clearing removes the reminder`() = runTest {
        val store = PausePreferences(preferences, StandardTestDispatcher(testScheduler))
        val observed = mutableListOf<String>()
        backgroundScope.launch { store.reminder.collect { observed.add(it) } }
        runCurrent(); store.saveReminder("Read a chapter"); runCurrent()
        store.saveReminder("  "); runCurrent()
        assertEquals(listOf("", "Read a chapter", ""), observed)
        assertEquals("", disk)
    }

    @Test fun `failed save preserves the previous reminder and permits retry`() = runTest {
        val store = PausePreferences(preferences, StandardTestDispatcher(testScheduler))
        store.saveReminder("Stretch")
        failWrites = true
        try { store.saveReminder("Drink water"); fail("Expected storage failure") } catch (_: IOException) { }
        assertEquals("Stretch", store.reminder.value)
        assertEquals("Stretch", memory)
        assertEquals("Stretch", disk)
        failWrites = false; store.saveReminder("Drink water")
        assertEquals("Drink water", store.reminder.value)
    }

    @Test fun `oversized or hidden control input is rejected before saving`() = runTest {
        val store = PausePreferences(preferences, StandardTestDispatcher(testScheduler))
        for (value in listOf("x".repeat(141), "hidden\u0000text")) {
            try { store.saveReminder(value); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
        }
        assertEquals("", memory)
        assertEquals("", disk)
    }

    @Test fun `Unicode and a reminder at the length limit are preserved`() = runTest {
        val store = PausePreferences(preferences, StandardTestDispatcher(testScheduler))
        store.saveReminder("Respira. \u4f11\u606f \u00e9")
        assertEquals("Respira. \u4f11\u606f \u00e9", store.reminder.value)
        store.saveReminder("x".repeat(140))
        assertEquals(140, store.reminder.value.length)
    }

    @Test fun `an invalid stored reminder falls back to an empty preference`() = runTest {
        memory = "x".repeat(141)
        val store = PausePreferences(preferences, StandardTestDispatcher(testScheduler))
        assertEquals("", store.reminder.value)
        store.saveReminder("Try again")
        assertEquals("Try again", disk)
    }
}

package com.dgraciano.breathe.domain

import org.junit.Assert.*
import org.junit.Test

class PausePolicyTest {
    @Test fun `Continue requires a displayed pause and its exact deadline`() {
        var now = 100L
        val pause = PausePolicy { now }
        assertFalse(pause.beginChoice(true))
        pause.start(5)
        now = 5_099
        assertEquals(1, pause.remainingSeconds())
        assertFalse(pause.beginChoice(true))
        now = 5_100
        assertEquals(0, pause.remainingSeconds())
        assertTrue(pause.beginChoice(true))
        assertFalse(pause.beginChoice(false))
    }

    @Test fun `Go back is immediate and a failed save allows the other choice`() {
        var now = 0L
        val pause = PausePolicy { now }
        assertTrue(pause.beginChoice(false))
        assertFalse(pause.beginChoice(false))
        pause.retryChoice()
        pause.start(5)
        now = 5_000
        assertTrue(pause.beginChoice(true))
    }

    @Test fun `rerendering cannot restart the countdown`() {
        var now = 0L
        val pause = PausePolicy { now }
        pause.start(5)
        now = 4_000
        pause.start(60)
        assertEquals(1, pause.remainingSeconds())
    }
}

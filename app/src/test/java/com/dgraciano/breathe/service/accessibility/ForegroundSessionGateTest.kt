package com.dgraciano.breathe.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundSessionGateTest {
    private val selected = setOf("com.social.app")

    @Test
    fun `first selected package transition emits one request`() {
        val gate = ForegroundSessionGate("com.dgraciano.breathe") { 1_000L }

        assertEquals(
            PauseRequest("com.social.app:1000:1", "com.social.app"),
            gate.onWindowStateChanged("com.social.app", selected)
        )
    }

    @Test
    fun `repeated events in one foreground session do not emit again`() {
        val gate = ForegroundSessionGate("com.dgraciano.breathe") { 1_000L }
        gate.onWindowStateChanged("com.social.app", selected)

        assertNull(gate.onWindowStateChanged("com.social.app", selected))
        assertNull(gate.onWindowStateChanged("com.social.app", selected))
    }

    @Test
    fun `breathe window does not reset selected app session`() {
        val gate = ForegroundSessionGate("com.dgraciano.breathe") { 1_000L }
        gate.onWindowStateChanged("com.social.app", selected)

        assertNull(gate.onWindowStateChanged("com.dgraciano.breathe", selected))
        assertNull(gate.onWindowStateChanged("com.social.app", selected))
    }

    @Test
    fun `leaving for another app permits a later selected app session`() {
        var time = 1_000L
        val gate = ForegroundSessionGate("com.dgraciano.breathe") { time }
        gate.onWindowStateChanged("com.social.app", selected)
        gate.onWindowStateChanged("com.android.launcher", selected)
        time = 2_000L

        assertEquals(
            PauseRequest("com.social.app:2000:2", "com.social.app"),
            gate.onWindowStateChanged("com.social.app", selected)
        )
    }

    @Test
    fun `blank and unselected packages do not emit`() {
        val gate = ForegroundSessionGate("com.dgraciano.breathe") { 1_000L }

        assertNull(gate.onWindowStateChanged(null, selected))
        assertNull(gate.onWindowStateChanged("", selected))
        assertNull(gate.onWindowStateChanged("com.mail.app", selected))
    }
}

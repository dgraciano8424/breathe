package com.dgraciano.breathe.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalRegistryTest {

    private var clock = 1_000_000L
    private fun registry(windowMs: Long = 5 * 60 * 1000L) =
        ApprovalRegistry(windowMs = windowMs) { clock }

    @Test
    fun `an app is not approved until the user chooses to continue`() {
        assertFalse(registry().isApproved("com.example.app"))
    }

    @Test
    fun `approving lets the app through`() {
        val reg = registry()
        reg.approve("com.example.app")
        assertTrue(reg.isApproved("com.example.app"))
    }

    @Test
    fun `approval does not leak to other apps`() {
        val reg = registry()
        reg.approve("com.example.app")
        assertFalse(reg.isApproved("com.other.app"))
    }

    @Test
    fun `approval expires once the window elapses`() {
        val reg = registry(windowMs = 60_000L)
        reg.approve("com.example.app")
        clock += 59_000L
        assertTrue(reg.isApproved("com.example.app"))
        clock += 2_000L
        assertFalse(reg.isApproved("com.example.app"))
    }

    @Test
    fun `turning back revokes approval immediately`() {
        val reg = registry()
        reg.approve("com.example.app")
        reg.revoke("com.example.app")
        assertFalse(reg.isApproved("com.example.app"))
    }

    /**
     * Regression test for the permanent-bypass leak. The old implementation evicted only
     * the single "last foreground" package, so once that reference was nulled — which
     * happened whenever the user sat inside an app without triggering a transition —
     * the approved app was never evicted again.
     */
    @Test
    fun `approval cannot outlive its window even if no other app is ever seen`() {
        val reg = registry(windowMs = 60_000L)
        reg.approve("com.example.app")

        // No intervening package, no eviction call, no foreground events at all.
        clock += 24 * 60 * 60 * 1000L

        assertFalse(
            "An approval must expire on wall-clock time, not on observing another app",
            reg.isApproved("com.example.app")
        )
    }

    @Test
    fun `re-approving extends the window from the new choice`() {
        val reg = registry(windowMs = 60_000L)
        reg.approve("com.example.app")
        clock += 50_000L
        reg.approve("com.example.app")
        clock += 30_000L
        assertTrue(reg.isApproved("com.example.app"))
    }

    @Test
    fun `clear drops every standing approval`() {
        val reg = registry()
        reg.approve("com.a")
        reg.approve("com.b")
        reg.clear()
        assertFalse(reg.isApproved("com.a"))
        assertFalse(reg.isApproved("com.b"))
    }
}

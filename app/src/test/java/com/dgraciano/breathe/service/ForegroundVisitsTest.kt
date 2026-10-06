package com.dgraciano.breathe.service

import org.junit.Assert.*
import org.junit.Test

class ForegroundVisitsTest {
    @Test fun `home departure dismisses pause and identifies approval to revoke`() {
        val visits = ForegroundVisits()
        visits.observe("messages", null, false)
        assertEquals(ForegroundChange("messages", true), visits.observe("launcher", "messages", false))
        assertEquals(ForegroundChange(null, false), visits.observe("launcher", null, false))
    }
    @Test fun `system overlays and keyboard do not end the underlying visit`() {
        val visits = ForegroundVisits()
        visits.observe("messages", null, false)
        assertNull(visits.observe("systemui", "messages", true))
        assertNull(visits.observe("keyboard", "messages", true))
        assertNull(visits.observe("breathe-overlay", "messages", true))
        assertEquals(ForegroundChange(null, false), visits.observe("messages", "messages", false))
        assertEquals(ForegroundChange("messages", true), visits.observe("other-app", "messages", false))
    }
    @Test fun `screen lock reset forgets the prior foreground visit`() {
        val visits = ForegroundVisits()
        visits.observe("messages", null, false)
        visits.reset()
        assertEquals(ForegroundChange(null, false), visits.observe("messages", null, false))
        val approvals = SessionApprovalStore()
        approvals.approve("messages")
        approvals.approve("other-app")
        approvals.clear()
        assertFalse(approvals.isApproved("messages"))
        assertFalse(approvals.isApproved("other-app"))
    }
    @Test fun `connection loading and display failures have distinct states`() {
        val status = MonitoringStatus()
        assertFalse(status.state.value.connected)
        status.connected()
        assertFalse(status.state.value.appsLoaded)
        status.loaded()
        status.failed()
        assertNotNull(status.state.value.issue)
        status.pauseShown(123)
        assertNull(status.state.value.issue)
        assertEquals(123L, status.state.value.lastPauseAt)
        status.disconnected()
        assertFalse(status.state.value.connected)
        assertFalse(status.state.value.appsLoaded)
    }
}

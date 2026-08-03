package com.dgraciano.breathe.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityPauseCoordinatorTest {
    @Test
    fun `selected transition invokes launch callback`() {
        val launches = mutableListOf<PauseRequest>()
        val coordinator = AccessibilityPauseCoordinator(
            gate = ForegroundSessionGate("com.dgraciano.breathe") { 42L },
            selectedPackages = { setOf("com.social.app") },
            launchPause = launches::add
        )

        coordinator.onWindowStateChanged("com.social.app")

        assertEquals(
            listOf(PauseRequest("com.social.app:42:1", "com.social.app")),
            launches
        )
    }

    @Test
    fun `unselected and duplicate transitions never launch`() {
        val launches = mutableListOf<PauseRequest>()
        val coordinator = AccessibilityPauseCoordinator(
            gate = ForegroundSessionGate("com.dgraciano.breathe") { 42L },
            selectedPackages = { setOf("com.social.app") },
            launchPause = launches::add
        )

        coordinator.onWindowStateChanged("com.mail.app")
        coordinator.onWindowStateChanged("com.social.app")
        coordinator.onWindowStateChanged("com.social.app")

        assertEquals(1, launches.size)
    }

    @Test
    fun `empty selected package snapshot never launches`() {
        val launches = mutableListOf<PauseRequest>()
        val coordinator = AccessibilityPauseCoordinator(
            gate = ForegroundSessionGate("com.dgraciano.breathe") { 42L },
            selectedPackages = { emptySet() },
            launchPause = launches::add
        )

        coordinator.onWindowStateChanged("com.social.app")

        assertTrue(launches.isEmpty())
    }
}

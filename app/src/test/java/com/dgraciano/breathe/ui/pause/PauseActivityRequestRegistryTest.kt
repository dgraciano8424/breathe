package com.dgraciano.breathe.ui.pause

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseActivityRequestRegistryTest {

    private val registry = PauseActivityRequestRegistry()

    @Test
    fun `closed request cannot be revived by a queued Activity launch`() {
        registry.close(REQUEST_ONE)
        registry.open(REQUEST_ONE)

        assertFalse(registry.isOpen(REQUEST_ONE))
        assertFalse(registry.claimAcknowledgement(REQUEST_ONE))
    }

    @Test
    fun `recreation can claim one acknowledgement for a request`() {
        registry.open(REQUEST_ONE)

        assertTrue(registry.claimAcknowledgement(REQUEST_ONE))
        assertFalse(registry.claimAcknowledgement(REQUEST_ONE))
        assertTrue(registry.isAcknowledged(REQUEST_ONE))
    }

    @Test
    fun `retargeted request has its own acknowledgement claim`() {
        registry.open(REQUEST_ONE)
        assertTrue(registry.claimAcknowledgement(REQUEST_ONE))
        registry.close(REQUEST_ONE)

        registry.open(REQUEST_TWO)

        assertTrue(registry.claimAcknowledgement(REQUEST_TWO))
        assertFalse(registry.claimAcknowledgement(REQUEST_TWO))
    }

    private companion object {
        const val REQUEST_ONE = "request-one"
        const val REQUEST_TWO = "request-two"
    }
}

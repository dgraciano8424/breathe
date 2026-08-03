package com.dgraciano.breathe.service.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceStateTest {
    private val expected =
        "com.dgraciano.breathe/com.dgraciano.breathe.service.accessibility.BreatheAccessibilityService"

    @Test
    fun `finds exact service in colon separated setting`() {
        val enabled = "com.reader/.ReaderService:$expected:com.voice/.VoiceService"
        assertTrue(AccessibilityServiceState.containsService(enabled, expected))
    }

    @Test
    fun `matching is case insensitive but not partial`() {
        assertTrue(AccessibilityServiceState.containsService(expected.uppercase(), expected))
        assertFalse(AccessibilityServiceState.containsService("prefix$expected", expected))
    }

    @Test
    fun `null and blank settings are disabled`() {
        assertFalse(AccessibilityServiceState.containsService(null, expected))
        assertFalse(AccessibilityServiceState.containsService("", expected))
    }
}

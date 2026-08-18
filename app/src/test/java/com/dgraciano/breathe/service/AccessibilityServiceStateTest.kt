package com.dgraciano.breathe.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceStateTest {

    @Test
    fun `matches only the exact service component in the application namespace`() {
        val enabledServices = listOf(
            "com.example.other/.service.BreatheAccessibilityService",
            "com.dgraciano.breathe.extra/.service.BreatheAccessibilityService",
            "com.dgraciano.breathe/.service.BreatheAccessibilityServiceOld",
            "com.dgraciano.breathe/.service.BreatheAccessibilityService",
        ).joinToString(":")

        assertTrue(
            AccessibilityServiceState.isEnabled(
                enabledServices = enabledServices,
                applicationPackage = "com.dgraciano.breathe",
                serviceClassName =
                    "com.dgraciano.breathe.service.BreatheAccessibilityService",
            ),
        )
    }

    @Test
    fun `rejects similarly named and wrong-namespace services`() {
        val enabledServices = listOf(
            "com.dgraciano.breathe.extra/.service.BreatheAccessibilityService",
            "com.dgraciano.breathe/.service.BreatheAccessibilityServiceOld",
            "com.dgraciano.breathe/com.dgraciano.breathe.service.LegacyBreatheAccessibilityService",
        ).joinToString(":")

        assertFalse(
            AccessibilityServiceState.isEnabled(
                enabledServices = enabledServices,
                applicationPackage = "com.dgraciano.breathe",
                serviceClassName =
                    "com.dgraciano.breathe.service.BreatheAccessibilityService",
            ),
        )
    }

    @Test
    fun `matches a fully qualified flattened component`() {
        assertTrue(
            AccessibilityServiceState.isEnabled(
                enabledServices =
                    "com.dgraciano.breathe/com.dgraciano.breathe.service.BreatheAccessibilityService",
                applicationPackage = "com.dgraciano.breathe",
                serviceClassName =
                    "com.dgraciano.breathe.service.BreatheAccessibilityService",
            ),
        )
    }
}

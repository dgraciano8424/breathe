package com.dgraciano.breathe.service

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
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

    @Test
    fun `merged manifest exposes exactly one signature-protected accessibility service`() {
        val manifest = listOf(
            File("app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"),
            File("build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"),
        ).first { it.isFile }
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(manifest)
        val matchingServices = document.getElementsByTagName("service")
            .let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) }
                    .filter {
                        it.attributes.getNamedItemNS(ANDROID_NAMESPACE, "name")?.nodeValue ==
                            "com.dgraciano.breathe.service.BreatheAccessibilityService"
                    }
            }

        assertEquals(1, matchingServices.size)
        val service = matchingServices.single()
        assertEquals(
            "true",
            service.attributes.getNamedItemNS(ANDROID_NAMESPACE, "exported")?.nodeValue,
        )
        assertEquals(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            service.attributes.getNamedItemNS(ANDROID_NAMESPACE, "permission")?.nodeValue,
        )
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}

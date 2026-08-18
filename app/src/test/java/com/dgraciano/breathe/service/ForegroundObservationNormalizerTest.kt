package com.dgraciano.breathe.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundObservationNormalizerTest {

    private val normalizer = ForegroundObservationNormalizer()

    @Test
    fun `Breathe package is classified before other package traits`() {
        val facts = facts(
            packageName = "com.dgraciano.breathe",
            activeImePackage = "com.dgraciano.breathe",
        )

        assertEquals(ForegroundSurface.BREATHE, normalizer.classify(facts))
    }

    @Test
    fun `System UI is classified as a system surface`() {
        val facts = facts(packageName = "com.android.systemui")

        assertEquals(ForegroundSurface.SYSTEM_UI, normalizer.classify(facts))
    }

    @Test
    fun `active IME is classified as system surface`() {
        val facts = facts(packageName = "com.keyboard", activeImePackage = "com.keyboard")

        assertEquals(ForegroundSurface.ACTIVE_IME, normalizer.classify(facts))
    }

    @Test
    fun `settings and permission packages are classified separately`() {
        val facts = facts(packageName = "com.android.settings")

        assertEquals(ForegroundSurface.SETTINGS_OR_PERMISSION, normalizer.classify(facts))
    }

    @Test
    fun `non-launchable package cannot become an eligible app`() {
        val facts = facts(packageName = "com.android.permissioncontroller")

        assertEquals(ForegroundSurface.NON_LAUNCHABLE, normalizer.classify(facts))
    }

    @Test
    fun `launchable third party package is eligible`() {
        val facts = facts(packageName = "com.social", launchablePackages = setOf("com.social"))

        assertEquals(ForegroundSurface.ELIGIBLE_APP, normalizer.classify(facts))
    }

    private fun facts(
        packageName: String,
        activeImePackage: String? = null,
        launchablePackages: Set<String> = setOf(
            "com.dgraciano.breathe",
            "com.android.systemui",
            "com.keyboard",
            "com.android.settings",
        ),
    ) = ForegroundFacts(
        packageName = packageName,
        ownPackage = "com.dgraciano.breathe",
        activeImePackage = activeImePackage,
        systemUiPackages = setOf("com.android.systemui"),
        settingsPackages = setOf("com.android.settings"),
        launchablePackages = launchablePackages,
    )
}

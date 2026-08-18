package com.dgraciano.breathe.service

/** Maps Android-resolved package facts into framework-free foreground surfaces. */
class ForegroundObservationNormalizer {

    fun classify(facts: ForegroundFacts): ForegroundSurface = when {
        facts.packageName == facts.ownPackage -> ForegroundSurface.BREATHE
        facts.packageName == facts.activeImePackage -> ForegroundSurface.ACTIVE_IME
        facts.packageName in facts.systemUiPackages -> ForegroundSurface.SYSTEM_UI
        facts.packageName in facts.settingsPackages -> ForegroundSurface.SETTINGS_OR_PERMISSION
        facts.packageName !in facts.launchablePackages -> ForegroundSurface.NON_LAUNCHABLE
        else -> ForegroundSurface.ELIGIBLE_APP
    }
}

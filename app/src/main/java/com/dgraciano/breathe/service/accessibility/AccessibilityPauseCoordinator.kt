package com.dgraciano.breathe.service.accessibility

class AccessibilityPauseCoordinator(
    private val gate: ForegroundSessionGate,
    private val selectedPackages: () -> Set<String>,
    private val launchPause: (PauseRequest) -> Unit
) {
    fun onWindowStateChanged(packageName: String?) {
        gate.onWindowStateChanged(packageName, selectedPackages())
            ?.let(launchPause)
    }
}

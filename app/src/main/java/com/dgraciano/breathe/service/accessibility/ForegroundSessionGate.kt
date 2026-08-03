package com.dgraciano.breathe.service.accessibility

data class PauseRequest(
    val sessionId: String,
    val packageName: String
)

class ForegroundSessionGate(
    private val breathePackage: String,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private var currentExternalPackage: String? = null
    private var requestSequence: Long = 0

    fun onWindowStateChanged(
        packageName: String?,
        selectedPackages: Set<String>
    ): PauseRequest? {
        if (packageName.isNullOrBlank() || packageName == breathePackage) return null
        if (packageName == currentExternalPackage) return null

        currentExternalPackage = packageName
        if (packageName !in selectedPackages) return null

        requestSequence += 1
        return PauseRequest(
            sessionId = "$packageName:${nowMillis()}:$requestSequence",
            packageName = packageName
        )
    }
}

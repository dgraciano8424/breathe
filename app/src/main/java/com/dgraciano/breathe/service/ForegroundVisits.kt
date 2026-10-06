package com.dgraciano.breathe.service

data class ForegroundChange(val departedPackage: String?, val dismissPause: Boolean)

/** Keeps a pause attached to its app while ignoring transient keyboard/system windows. */
class ForegroundVisits {
    private var currentPackage: String? = null
    fun observe(current: String, pausePackage: String?, transient: Boolean): ForegroundChange? {
        if (transient) return null
        val departed = currentPackage?.takeIf { it != current }
        currentPackage = current
        return ForegroundChange(departed, pausePackage != null && pausePackage != current)
    }
    fun reset() { currentPackage = null }
}

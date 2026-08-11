package com.dgraciano.breathe.service

/**
 * Tracks which apps the user has consciously chosen to continue into, and for how long.
 *
 * Replaces the previous `approvedSessions` set in AppMonitorService, which had two defects:
 *
 *  1. A package was marked approved *before* the pause was shown, so a pause that failed
 *     to appear counted as consent.
 *  2. Eviction only ever removed the single "last foreground" package. Foreground
 *     detection returned null whenever no transition happened in its window — the normal
 *     state when a user reads inside an app — which nulled that reference and made the
 *     next eviction a no-op, leaving the app approved for the life of the process.
 *
 * Approval here is granted only on an explicit choice and expires on wall-clock time, so
 * no missed or out-of-order event can produce a permanent bypass.
 */
class ApprovalRegistry(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val approvedUntil = mutableMapOf<String, Long>()

    /** Records a deliberate "continue into this app" choice. */
    fun approve(packageName: String) {
        approvedUntil[packageName] = now() + windowMs
    }

    fun isApproved(packageName: String): Boolean {
        val until = approvedUntil[packageName] ?: return false
        if (now() >= until) {
            approvedUntil.remove(packageName)
            return false
        }
        return true
    }

    /** Turning back revokes any standing approval immediately. */
    fun revoke(packageName: String) {
        approvedUntil.remove(packageName)
    }

    fun clear() = approvedUntil.clear()

    companion object {
        /** How long a conscious "continue" lasts before the user is asked again. */
        const val DEFAULT_WINDOW_MS = 5 * 60 * 1000L
    }
}

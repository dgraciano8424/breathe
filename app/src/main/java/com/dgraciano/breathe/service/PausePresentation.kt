package com.dgraciano.breathe.service

data class PauseRequest(
    val id: String,
    val packageName: String,
    val observedAtElapsedMs: Long,
)

interface PausePresentationCallbacks {
    fun onOverlayAdded(requestId: String)
    fun onOverlayFailed(requestId: String, cause: Throwable)
    fun onActivityContentReady(requestId: String)
    fun onPresentationLost(requestId: String, cause: Throwable?)
}

interface PausePresentation {
    fun show(request: PauseRequest, callbacks: PausePresentationCallbacks)
    fun dismiss(requestId: String)
}

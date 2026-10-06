package com.dgraciano.breathe.domain

/** Pause decisions without Android windows, storage, coroutines or UI dependencies. */
class PausePolicy(private val now: () -> Long) {
    private var deadline: Long? = null
    private var choiceStarted = false

    fun start(seconds: Int) {
        if (deadline == null) deadline = now() + seconds.coerceAtLeast(0) * 1000L
    }

    fun remainingSeconds(): Int = deadline?.let {
        ((it - now()).coerceAtLeast(0) + 999).div(1000).toInt()
    } ?: Int.MAX_VALUE

    fun beginChoice(proceed: Boolean): Boolean {
        if (choiceStarted || (proceed && remainingSeconds() > 0)) return false
        choiceStarted = true
        return true
    }

    /** A rejected durable write permits another choice without restarting the timer. */
    fun retryChoice() { choiceStarted = false }
}

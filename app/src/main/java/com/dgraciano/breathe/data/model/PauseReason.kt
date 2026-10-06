package com.dgraciano.breathe.data.model

data class PauseReason(val key: String, val label: String)

/** Stable stored keys, including the original reasons in existing choice history. */
val pauseReasons = listOf(
    PauseReason("WORK", "Work"),
    PauseReason("LEARN", "Learn"),
    PauseReason("RELAX", "Relax"),
    PauseReason("CONNECT", "Connect"),
    PauseReason(InterventionEvent.REASON_CURIOUS, "Curious"),
    PauseReason(InterventionEvent.REASON_BORED, "Bored"),
    PauseReason(InterventionEvent.REASON_HABIT, "Habit"),
    PauseReason(InterventionEvent.REASON_ESCAPING, "Escaping")
)

val pauseReasonKeys = pauseReasons.map { it.key }.toSet()

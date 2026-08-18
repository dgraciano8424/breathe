package com.dgraciano.breathe.data.repository

enum class DecisionOutcome {
    DECLINED,
    OPENED,
}

data class PauseDecision(
    val requestId: String,
    val packageName: String,
    val appName: String,
    val outcome: DecisionOutcome,
    val reason: String?,
)

interface PauseDecisionSink {
    suspend fun record(decision: PauseDecision): Result<Long>
}

package com.dgraciano.breathe.data.db

data class TodayAggregate(
    val attempts: Int,
    val declined: Int,
    val minutesSaved: Int,
)

data class LifetimeAggregate(
    val declined: Long,
    val minutesSaved: Long,
)

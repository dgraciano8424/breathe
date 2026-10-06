package com.dgraciano.breathe.ui.pause

import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.data.repository.ChoiceRecorder
import com.dgraciano.breathe.data.repository.MentalHealthTipsRepository
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.di.ApplicationScope
import com.dgraciano.breathe.service.PauseClock
import com.dgraciano.breathe.service.SessionApprovalStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Activity Hilt and non-Activity overlay hosts construct the same coordinator. */
class PauseViewModelFactory @Inject constructor(
    private val stats: StatsRepository,
    private val apps: AppRepository,
    private val tips: MentalHealthTipsRepository,
    private val approvals: SessionApprovalStore,
    private val choices: ChoiceRecorder,
    private val clock: PauseClock,
    @ApplicationScope private val scope: CoroutineScope
) {
    fun create() = PauseViewModel(stats, apps, tips, approvals, choices, clock, scope)
}

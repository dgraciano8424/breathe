package com.dgraciano.breathe.di

import com.dgraciano.breathe.data.repository.InterventionDecisionRepository
import com.dgraciano.breathe.data.repository.PauseDecisionSink
import com.dgraciano.breathe.service.PausePresentation
import com.dgraciano.breathe.service.SessionTimeHelper
import com.dgraciano.breathe.service.UsageAccessReader
import com.dgraciano.breathe.ui.pause.PauseOverlayHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PausePresentationModule {

    @Binds
    @Singleton
    abstract fun bindPausePresentation(host: PauseOverlayHost): PausePresentation

    @Binds
    @Singleton
    abstract fun bindPauseDecisionSink(
        repository: InterventionDecisionRepository,
    ): PauseDecisionSink

    @Binds
    @Singleton
    abstract fun bindUsageAccessReader(helper: SessionTimeHelper): UsageAccessReader
}

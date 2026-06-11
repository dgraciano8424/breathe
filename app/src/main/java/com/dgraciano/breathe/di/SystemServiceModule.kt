package com.dgraciano.breathe.di

import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SystemServiceModule {

    @Provides
    @Singleton
    fun provideUsageStatsManager(@ApplicationContext ctx: Context): UsageStatsManager =
        ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
}

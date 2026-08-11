package com.dgraciano.breathe.di

import android.content.Context
import androidx.room.Room
import com.dgraciano.breathe.data.db.BreatheDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): BreatheDatabase =
        Room.databaseBuilder(ctx, BreatheDatabase::class.java, "breathe.db")
            .addMigrations(
                BreatheDatabase.MIGRATION_1_2,
                BreatheDatabase.MIGRATION_2_3,
                BreatheDatabase.MIGRATION_3_4,
                BreatheDatabase.MIGRATION_4_5
            )
            .build()

    @Provides fun provideBlockedAppDao(db: BreatheDatabase) = db.blockedAppDao()
    @Provides fun provideInterventionEventDao(db: BreatheDatabase) = db.interventionEventDao()
}

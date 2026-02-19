// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import com.podometer.data.db.ActivityTransitionDao
import com.podometer.data.db.CyclingSessionDao
import com.podometer.data.db.StepDao
import com.podometer.data.repository.CyclingRepository
import com.podometer.data.repository.StepRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for repository bindings.
 *
 * Scoped to [SingletonComponent] so repositories are singletons shared
 * across the application.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideStepRepository(
        stepDao: StepDao,
        activityTransitionDao: ActivityTransitionDao,
    ): StepRepository = StepRepository(stepDao, activityTransitionDao)

    @Provides
    @Singleton
    fun provideCyclingRepository(
        cyclingSessionDao: CyclingSessionDao,
    ): CyclingRepository = CyclingRepository(cyclingSessionDao)
}

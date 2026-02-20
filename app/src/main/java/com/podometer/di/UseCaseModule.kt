// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import com.podometer.domain.usecase.GetTodayCyclingSessionsUseCase
import com.podometer.domain.usecase.GetTodayCyclingSessionsUseCaseImpl
import com.podometer.domain.usecase.GetTodayStepsUseCase
import com.podometer.domain.usecase.GetTodayStepsUseCaseImpl
import com.podometer.domain.usecase.GetTodayTransitionsUseCase
import com.podometer.domain.usecase.GetTodayTransitionsUseCaseImpl
import com.podometer.domain.usecase.GetWeeklyStepsUseCase
import com.podometer.domain.usecase.GetWeeklyStepsUseCaseImpl
import com.podometer.domain.usecase.OverrideActivityUseCase
import com.podometer.domain.usecase.OverrideActivityUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds domain use case interfaces to their implementations.
 *
 * Scoped to [SingletonComponent] so use case instances are shared across the app.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UseCaseModule {

    /** Binds [GetTodayStepsUseCaseImpl] to the [GetTodayStepsUseCase] interface. */
    @Binds
    @Singleton
    abstract fun bindGetTodayStepsUseCase(impl: GetTodayStepsUseCaseImpl): GetTodayStepsUseCase

    /** Binds [GetWeeklyStepsUseCaseImpl] to the [GetWeeklyStepsUseCase] interface. */
    @Binds
    @Singleton
    abstract fun bindGetWeeklyStepsUseCase(impl: GetWeeklyStepsUseCaseImpl): GetWeeklyStepsUseCase

    /** Binds [GetTodayTransitionsUseCaseImpl] to the [GetTodayTransitionsUseCase] interface. */
    @Binds
    @Singleton
    abstract fun bindGetTodayTransitionsUseCase(impl: GetTodayTransitionsUseCaseImpl): GetTodayTransitionsUseCase

    /** Binds [GetTodayCyclingSessionsUseCaseImpl] to the [GetTodayCyclingSessionsUseCase] interface. */
    @Binds
    @Singleton
    abstract fun bindGetTodayCyclingSessionsUseCase(impl: GetTodayCyclingSessionsUseCaseImpl): GetTodayCyclingSessionsUseCase

    /** Binds [OverrideActivityUseCaseImpl] to the [OverrideActivityUseCase] interface. */
    @Binds
    @Singleton
    abstract fun bindOverrideActivityUseCase(impl: OverrideActivityUseCaseImpl): OverrideActivityUseCase
}

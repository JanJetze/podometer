// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import android.os.Build
import com.podometer.domain.usecase.ExportDataUseCase
import com.podometer.domain.usecase.GetStreakUseCase
import com.podometer.domain.usecase.GetStreakUseCaseImpl
import com.podometer.domain.usecase.ImportDataUseCase
import com.podometer.domain.usecase.GetTodayStepsUseCase
import com.podometer.domain.usecase.GetTodayStepsUseCaseImpl
import com.podometer.domain.usecase.GetWeeklyStepsUseCase
import com.podometer.domain.usecase.GetWeeklyStepsUseCaseImpl
import com.podometer.data.db.StepBucketDao
import com.podometer.data.db.StepDao
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.StepBucketRepository
import com.podometer.data.repository.StepRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
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

    /** Binds [GetStreakUseCaseImpl] to the [GetStreakUseCase] interface. */
    @Binds
    @Singleton
    abstract fun bindGetStreakUseCase(impl: GetStreakUseCaseImpl): GetStreakUseCase

    companion object {
        /**
         * Provides [ExportDataUseCase] with the device model string resolved at runtime.
         *
         * Using a [Provides] function in the companion object allows mixing [Binds]
         * (in the abstract class) with [Provides] (in the companion object) within
         * the same Hilt module.
         */
        @Provides
        @Singleton
        fun provideExportDataUseCase(
            stepRepository: StepRepository,
            stepBucketRepository: StepBucketRepository,
        ): ExportDataUseCase = ExportDataUseCase(
            stepRepository = stepRepository,
            stepBucketRepository = stepBucketRepository,
            deviceModel = Build.MODEL,
        )

        @Provides
        @Singleton
        fun provideImportDataUseCase(
            stepDao: StepDao,
            stepBucketDao: StepBucketDao,
        ): ImportDataUseCase = ImportDataUseCase(
            stepDao = stepDao,
            stepBucketDao = stepBucketDao,
        )
    }
}

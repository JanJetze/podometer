// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import android.os.Build
import androidx.room.withTransaction
import com.podometer.data.db.PodometerDatabase
import com.podometer.domain.usecase.ExportDataUseCase
import com.podometer.domain.usecase.ImportDataUseCase
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
import com.podometer.domain.usecase.RecomputeActivitySessionsUseCase
import com.podometer.domain.usecase.RecomputeActivitySessionsUseCaseImpl
import com.podometer.domain.usecase.TransactionRunner
import com.podometer.data.db.ActivityTransitionDao
import com.podometer.data.db.CyclingSessionDao
import com.podometer.data.db.SensorWindowDao
import com.podometer.data.db.StepDao
import com.podometer.data.repository.CyclingRepository
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

    /** Binds [RecomputeActivitySessionsUseCaseImpl] to the [RecomputeActivitySessionsUseCase] interface. */
    @Binds
    @Singleton
    abstract fun bindRecomputeActivitySessionsUseCase(impl: RecomputeActivitySessionsUseCaseImpl): RecomputeActivitySessionsUseCase

    companion object {
        /**
         * Provides a [TransactionRunner] backed by [PodometerDatabase.withTransaction].
         *
         * All DB writes executed through this runner are wrapped in a single Room transaction,
         * ensuring that cross-DAO operations are atomic.
         */
        @Provides
        @Singleton
        fun provideTransactionRunner(database: PodometerDatabase): TransactionRunner =
            object : TransactionRunner {
                override suspend fun <R> run(block: suspend () -> R): R =
                    database.withTransaction(block)
            }

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
            cyclingRepository: CyclingRepository,
            sensorWindowDao: SensorWindowDao,
        ): ExportDataUseCase = ExportDataUseCase(
            stepRepository = stepRepository,
            cyclingRepository = cyclingRepository,
            sensorWindowDao = sensorWindowDao,
            deviceModel = Build.MODEL,
        )

        @Provides
        @Singleton
        fun provideImportDataUseCase(
            stepDao: StepDao,
            activityTransitionDao: ActivityTransitionDao,
            cyclingSessionDao: CyclingSessionDao,
            sensorWindowDao: SensorWindowDao,
        ): ImportDataUseCase = ImportDataUseCase(
            stepDao = stepDao,
            activityTransitionDao = activityTransitionDao,
            cyclingSessionDao = cyclingSessionDao,
            sensorWindowDao = sensorWindowDao,
        )
    }
}

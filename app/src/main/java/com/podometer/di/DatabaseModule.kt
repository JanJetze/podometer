// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import android.content.Context
import androidx.room.Room
import com.podometer.data.db.ActivityTransitionDao
import com.podometer.data.db.CyclingSessionDao
import com.podometer.data.db.PodometerDatabase
import com.podometer.data.db.SensorWindowDao
import com.podometer.data.db.StepDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Room database bindings.
 *
 * Scoped to [SingletonComponent] so the database instance and all DAO
 * instances live for the lifetime of the application.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePodometerDatabase(
        @ApplicationContext context: Context,
    ): PodometerDatabase =
        Room.databaseBuilder(
            context,
            PodometerDatabase::class.java,
            "podometer.db",
        )
            .addMigrations(PodometerDatabase.MIGRATION_1_2)
            .build()

    @Provides
    @Singleton
    fun provideStepDao(database: PodometerDatabase): StepDao =
        database.stepDao()

    @Provides
    @Singleton
    fun provideActivityTransitionDao(database: PodometerDatabase): ActivityTransitionDao =
        database.activityTransitionDao()

    @Provides
    @Singleton
    fun provideCyclingSessionDao(database: PodometerDatabase): CyclingSessionDao =
        database.cyclingSessionDao()

    @Provides
    @Singleton
    fun provideSensorWindowDao(database: PodometerDatabase): SensorWindowDao =
        database.sensorWindowDao()
}

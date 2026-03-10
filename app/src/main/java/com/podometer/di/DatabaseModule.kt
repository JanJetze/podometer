// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import android.content.Context
import androidx.room.Room
import com.podometer.data.db.PodometerDatabase
import com.podometer.data.db.StepBucketDao
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideStepDao(database: PodometerDatabase): StepDao =
        database.stepDao()

    @Provides
    @Singleton
    fun provideStepBucketDao(database: PodometerDatabase): StepBucketDao =
        database.stepBucketDao()
}

// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import android.content.Context
import android.hardware.SensorManager
import com.podometer.data.sensor.StepSensorManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for sensor manager bindings.
 *
 * Scoped to [SingletonComponent] so sensor managers are shared across the
 * entire application.
 */
@Module
@InstallIn(SingletonComponent::class)
object SensorModule {

    /**
     * Provides the Android system [SensorManager].
     *
     * Obtained from [ApplicationContext] so it is safe to retain for the
     * lifetime of the application process.
     */
    @Provides
    @Singleton
    fun provideSensorManager(
        @ApplicationContext context: Context,
    ): SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /**
     * Provides the application-scoped [StepSensorManager].
     *
     * The instance is a singleton so a single [SensorManager] registration is
     * shared across all consumers (e.g., the foreground service).
     */
    @Provides
    @Singleton
    fun provideStepSensorManager(
        sensorManager: SensorManager,
    ): StepSensorManager = StepSensorManager(sensorManager)
}

// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for sensor manager bindings.
 *
 * Scoped to [SingletonComponent] so sensor managers are shared across the
 * entire application. Actual @Provides methods will be added once the
 * sensor layer is implemented.
 */
@Module
@InstallIn(SingletonComponent::class)
object SensorModule

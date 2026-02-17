// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for Room database bindings.
 *
 * Scoped to [SingletonComponent] so the database instance lives for the
 * lifetime of the application. Actual @Provides methods will be added once
 * the Room database is implemented.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule

// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for repository bindings.
 *
 * Scoped to [SingletonComponent] so repositories are singletons shared
 * across the application. Actual @Provides/@Binds methods will be added
 * once the repository layer is implemented.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule

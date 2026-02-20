// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.podometer.data.repository.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Top-level DataStore extension — one instance per application process.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Hilt module for DataStore Preferences bindings.
 *
 * Provides a singleton [DataStore] instance and the [PreferencesManager]
 * that wraps it. Scoped to [SingletonComponent] so the store lives for the
 * lifetime of the application.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun providePreferencesManager(
        dataStore: DataStore<Preferences>,
    ): PreferencesManager = PreferencesManager(dataStore)
}

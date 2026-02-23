// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.podometer.data.repository.PreferencesManager

/**
 * Unit tests for [OnboardingViewModel] and the supporting [PreferencesManager]
 * onboarding preference methods added for this task.
 *
 * Tests exercise the pure-Kotlin helper logic reachable without an Android runtime.
 */
class OnboardingViewModelTest {

    // ─── Fake DataStore ──────────────────────────────────────────────────────

    private class FakeDataStore(
        private val initial: Preferences = preferencesOf(),
    ) : DataStore<Preferences> {

        private var current: Preferences = initial
        var lastWritten: Preferences? = null

        override val data: Flow<Preferences>
            get() = flowOf(current)

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(current)
            lastWritten = updated
            current = updated
            return updated
        }
    }

    // ─── isOnboardingComplete: default value ─────────────────────────────────

    @Test
    fun `isOnboardingComplete emits false by default when key is absent`() = runTest {
        val fakeStore = FakeDataStore(initial = preferencesOf())
        val manager = PreferencesManager(fakeStore)

        val result = manager.isOnboardingComplete().first()

        assertFalse("Default onboarding-complete should be false", result)
    }

    // ─── isOnboardingComplete: stored value ──────────────────────────────────

    @Test
    fun `isOnboardingComplete emits stored true value`() = runTest {
        val key = booleanPreferencesKey("onboarding_complete")
        val fakeStore = FakeDataStore(initial = preferencesOf(key to true))
        val manager = PreferencesManager(fakeStore)

        val result = manager.isOnboardingComplete().first()

        assertTrue(result)
    }

    @Test
    fun `isOnboardingComplete emits stored false value`() = runTest {
        val key = booleanPreferencesKey("onboarding_complete")
        val fakeStore = FakeDataStore(initial = preferencesOf(key to false))
        val manager = PreferencesManager(fakeStore)

        val result = manager.isOnboardingComplete().first()

        assertFalse(result)
    }

    // ─── setOnboardingComplete ────────────────────────────────────────────────

    @Test
    fun `setOnboardingComplete writes true under correct key`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setOnboardingComplete(true)

        val key = booleanPreferencesKey("onboarding_complete")
        val written = fakeStore.lastWritten
        assertEquals(true, written!![key])
    }

    @Test
    fun `setOnboardingComplete writes false under correct key`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setOnboardingComplete(false)

        val key = booleanPreferencesKey("onboarding_complete")
        val written = fakeStore.lastWritten
        assertEquals(false, written!![key])
    }

    // ─── Round-trip ───────────────────────────────────────────────────────────

    @Test
    fun `setOnboardingComplete round-trips through isOnboardingComplete`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setOnboardingComplete(true)

        val result = manager.isOnboardingComplete().first()
        assertTrue(result)
    }

    // ─── Key name contract ────────────────────────────────────────────────────

    @Test
    fun `isOnboardingComplete uses key named onboarding_complete`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setOnboardingComplete(true)

        val key = booleanPreferencesKey("onboarding_complete")
        assertEquals(true, fakeStore.lastWritten!![key])
    }

    // ─── resolveStartDestination helper ──────────────────────────────────────

    @Test
    fun `resolveStartDestination returns onboarding when not complete`() {
        val result = resolveStartDestination(isOnboardingComplete = false)
        assertEquals("onboarding", result)
    }

    @Test
    fun `resolveStartDestination returns dashboard when complete`() {
        val result = resolveStartDestination(isOnboardingComplete = true)
        assertEquals("dashboard", result)
    }
}

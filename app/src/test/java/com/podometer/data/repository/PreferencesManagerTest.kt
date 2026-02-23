// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PreferencesManager].
 *
 * Uses a fake in-memory [DataStore] to avoid Android dependencies while
 * verifying the auto-start preference key name, default value, and read/write
 * delegation.
 */
class PreferencesManagerTest {

    // ─── Fake DataStore ──────────────────────────────────────────────────────

    /**
     * Minimal fake DataStore backed by an in-memory [Preferences] snapshot.
     *
     * [updateData] captures the last transform result so tests can inspect
     * what value was written.
     */
    private class FakeDataStore(
        private val initial: Preferences = preferencesOf(),
    ) : DataStore<Preferences> {

        private var current: Preferences = initial
        var lastWritten: Preferences? = null

        override val data: Flow<Preferences>
            get() = flowOf(current)

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(current)
            lastWritten = updated
            current = updated
            return updated
        }
    }

    // ─── isAutoStartEnabled: default value ───────────────────────────────────

    @Test
    fun `isAutoStartEnabled emits true by default when key is absent`() = runTest {
        val fakeStore = FakeDataStore(initial = preferencesOf())
        val manager = PreferencesManager(fakeStore)

        val result = manager.isAutoStartEnabled().first()

        assertTrue("Default auto-start should be true", result)
    }

    // ─── isAutoStartEnabled: stored value ────────────────────────────────────

    @Test
    fun `isAutoStartEnabled emits stored true value`() = runTest {
        val key = booleanPreferencesKey("auto_start_enabled")
        val fakeStore = FakeDataStore(initial = preferencesOf(key to true))
        val manager = PreferencesManager(fakeStore)

        val result = manager.isAutoStartEnabled().first()

        assertTrue(result)
    }

    @Test
    fun `isAutoStartEnabled emits stored false value`() = runTest {
        val key = booleanPreferencesKey("auto_start_enabled")
        val fakeStore = FakeDataStore(initial = preferencesOf(key to false))
        val manager = PreferencesManager(fakeStore)

        val result = manager.isAutoStartEnabled().first()

        assertFalse(result)
    }

    // ─── setAutoStartEnabled ─────────────────────────────────────────────────

    @Test
    fun `setAutoStartEnabled writes false under correct key`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setAutoStartEnabled(false)

        val key = booleanPreferencesKey("auto_start_enabled")
        val written = fakeStore.lastWritten
        assertNotNull("DataStore updateData must have been called", written)
        assertEquals(false, written!![key])
    }

    @Test
    fun `setAutoStartEnabled writes true under correct key`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setAutoStartEnabled(true)

        val key = booleanPreferencesKey("auto_start_enabled")
        val written = fakeStore.lastWritten
        assertNotNull("DataStore updateData must have been called", written)
        assertEquals(true, written!![key])
    }

    // ─── Key name contract ────────────────────────────────────────────────────

    @Test
    fun `isAutoStartEnabled uses key named auto_start_enabled`() = runTest {
        // Write via setAutoStartEnabled and read back using the known key name
        // to confirm the same key is used for both operations.
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setAutoStartEnabled(false)

        val key = booleanPreferencesKey("auto_start_enabled")
        val value = manager.isAutoStartEnabled().first()
        assertFalse("Should reflect written false value", value)
        assertEquals(false, fakeStore.lastWritten!![key])
    }

    // ─── strideLengthKm: default value ───────────────────────────────────────

    @Test
    fun `strideLengthKm emits default 0_00075f when key is absent`() = runTest {
        val fakeStore = FakeDataStore(initial = preferencesOf())
        val manager = PreferencesManager(fakeStore)

        val result = manager.strideLengthKm().first()

        assertEquals(0.00075f, result, 0.0000001f)
    }

    // ─── strideLengthKm: stored value ────────────────────────────────────────

    @Test
    fun `strideLengthKm emits stored custom value`() = runTest {
        val key = floatPreferencesKey("stride_length_km")
        val fakeStore = FakeDataStore(initial = preferencesOf(key to 0.0009f))
        val manager = PreferencesManager(fakeStore)

        val result = manager.strideLengthKm().first()

        assertEquals(0.0009f, result, 0.0000001f)
    }

    // ─── setStrideLengthKm ───────────────────────────────────────────────────

    @Test
    fun `setStrideLengthKm writes value under correct key`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setStrideLengthKm(0.001f)

        val key = floatPreferencesKey("stride_length_km")
        val written = fakeStore.lastWritten
        assertNotNull("DataStore updateData must have been called", written)
        assertEquals(0.001f, written!![key]!!, 0.0000001f)
    }

    @Test
    fun `strideLengthKm uses key named stride_length_km`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setStrideLengthKm(0.0008f)

        val key = floatPreferencesKey("stride_length_km")
        val value = manager.strideLengthKm().first()
        assertEquals(0.0008f, value, 0.0000001f)
        assertEquals(0.0008f, fakeStore.lastWritten!![key]!!, 0.0000001f)
    }

    // ─── setStrideLengthKm: validation ───────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `setStrideLengthKm rejects zero stride`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setStrideLengthKm(0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setStrideLengthKm rejects negative stride`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setStrideLengthKm(-0.001f)
    }

    @Test
    fun `setStrideLengthKm coerces value above maximum to 0_005f`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setStrideLengthKm(0.1f) // 100 m — unreasonable

        val key = floatPreferencesKey("stride_length_km")
        assertEquals(0.005f, fakeStore.lastWritten!![key]!!, 0.0000001f)
    }

    @Test
    fun `setStrideLengthKm coerces value below minimum to 0_0001f`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setStrideLengthKm(0.00001f) // 1 cm — unreasonable

        val key = floatPreferencesKey("stride_length_km")
        assertEquals(0.0001f, fakeStore.lastWritten!![key]!!, 0.0000001f)
    }

    // ─── Class existence ──────────────────────────────────────────────────────

    @Test
    fun `PreferencesManager class exists in repository package`() {
        val clazz = PreferencesManager::class.java
        assertNotNull(clazz)
        assertTrue(
            "PreferencesManager must be in com.podometer.data.repository",
            clazz.name == "com.podometer.data.repository.PreferencesManager",
        )
    }

    // ─── dailyStepGoal: default value ────────────────────────────────────────

    @Test
    fun `dailyStepGoal emits 10000 by default when key is absent`() = runTest {
        val fakeStore = FakeDataStore(initial = preferencesOf())
        val manager = PreferencesManager(fakeStore)

        val result = manager.dailyStepGoal().first()

        assertEquals(10_000, result)
    }

    // ─── dailyStepGoal: stored value ─────────────────────────────────────────

    @Test
    fun `dailyStepGoal emits stored custom value`() = runTest {
        val key = intPreferencesKey("daily_step_goal")
        val fakeStore = FakeDataStore(initial = preferencesOf(key to 8_000))
        val manager = PreferencesManager(fakeStore)

        val result = manager.dailyStepGoal().first()

        assertEquals(8_000, result)
    }

    // ─── setDailyStepGoal ────────────────────────────────────────────────────

    @Test
    fun `setDailyStepGoal writes value under correct key`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setDailyStepGoal(12_000)

        val key = intPreferencesKey("daily_step_goal")
        val written = fakeStore.lastWritten
        assertNotNull("DataStore updateData must have been called", written)
        assertEquals(12_000, written!![key])
    }

    @Test
    fun `setDailyStepGoal round-trips through read`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setDailyStepGoal(5_000)

        val result = manager.dailyStepGoal().first()
        assertEquals(5_000, result)
    }

    @Test
    fun `setDailyStepGoal uses key named daily_step_goal`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setDailyStepGoal(7_500)

        val key = intPreferencesKey("daily_step_goal")
        assertEquals(7_500, fakeStore.lastWritten!![key])
    }

    // ─── notificationStyle: default value ────────────────────────────────────

    @Test
    fun `notificationStyle emits minimal by default when key is absent`() = runTest {
        val fakeStore = FakeDataStore(initial = preferencesOf())
        val manager = PreferencesManager(fakeStore)

        val result = manager.notificationStyle().first()

        assertEquals("minimal", result)
    }

    // ─── notificationStyle: stored value ─────────────────────────────────────

    @Test
    fun `notificationStyle emits stored detailed value`() = runTest {
        val key = stringPreferencesKey("notification_style")
        val fakeStore = FakeDataStore(initial = preferencesOf(key to "detailed"))
        val manager = PreferencesManager(fakeStore)

        val result = manager.notificationStyle().first()

        assertEquals("detailed", result)
    }

    // ─── setNotificationStyle ─────────────────────────────────────────────────

    @Test
    fun `setNotificationStyle writes value under correct key`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setNotificationStyle("detailed")

        val key = stringPreferencesKey("notification_style")
        val written = fakeStore.lastWritten
        assertNotNull("DataStore updateData must have been called", written)
        assertEquals("detailed", written!![key])
    }

    @Test
    fun `setNotificationStyle round-trips through read`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setNotificationStyle("detailed")

        val result = manager.notificationStyle().first()
        assertEquals("detailed", result)
    }

    @Test
    fun `setNotificationStyle uses key named notification_style`() = runTest {
        val fakeStore = FakeDataStore()
        val manager = PreferencesManager(fakeStore)

        manager.setNotificationStyle("minimal")

        val key = stringPreferencesKey("notification_style")
        assertEquals("minimal", fakeStore.lastWritten!![key])
    }
}

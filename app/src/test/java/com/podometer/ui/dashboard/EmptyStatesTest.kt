// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import com.podometer.data.sensor.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the composable-package existence and [DashboardUiState] new fields
 * for [EmptyStates] functionality.
 *
 * Compose composables cannot be rendered in JVM unit tests, so these tests verify:
 * - The new [DashboardUiState] fields exist with correct default values.
 * - The [EmptyStatesKt] class is compiled into the expected package.
 */
class EmptyStatesTest {

    // ─── DashboardUiState new fields ─────────────────────────────────────────

    @Test
    fun `DashboardUiState default sensorType is STEP_COUNTER`() {
        val state = DashboardUiState()
        assertEquals(SensorType.STEP_COUNTER, state.sensorType)
    }

    @Test
    fun `DashboardUiState default permissionsDenied is false`() {
        val state = DashboardUiState()
        assertFalse(state.permissionsDenied)
    }

    @Test
    fun `DashboardUiState can be created with sensorType ACCELEROMETER`() {
        val state = DashboardUiState(sensorType = SensorType.ACCELEROMETER)
        assertEquals(SensorType.ACCELEROMETER, state.sensorType)
    }

    @Test
    fun `DashboardUiState can be created with sensorType NONE`() {
        val state = DashboardUiState(sensorType = SensorType.NONE)
        assertEquals(SensorType.NONE, state.sensorType)
    }

    @Test
    fun `DashboardUiState can be created with permissionsDenied true`() {
        val state = DashboardUiState(permissionsDenied = true)
        assertTrue(state.permissionsDenied)
    }

    @Test
    fun `DashboardUiState copy preserves new fields`() {
        val original = DashboardUiState(
            sensorType = SensorType.ACCELEROMETER,
            permissionsDenied = true,
        )
        val copy = original.copy(todaySteps = 500)
        assertEquals(SensorType.ACCELEROMETER, copy.sensorType)
        assertTrue(copy.permissionsDenied)
        assertEquals(500, copy.todaySteps)
    }

    // ─── isFirstLaunch condition ──────────────────────────────────────────────

    @Test
    fun `isFirstLaunch condition is true when steps zero and transitions empty`() {
        val state = DashboardUiState(todaySteps = 0, transitions = emptyList())
        assertTrue(state.todaySteps == 0 && state.transitions.isEmpty())
    }

    @Test
    fun `isFirstLaunch condition is false when steps are non-zero`() {
        val state = DashboardUiState(todaySteps = 100, transitions = emptyList())
        assertFalse(state.todaySteps == 0 && state.transitions.isEmpty())
    }

    @Test
    fun `isFirstLaunch condition is false when transitions are non-empty`() {
        val state = DashboardUiState(
            todaySteps = 0,
            transitions = listOf(
                com.podometer.domain.model.TransitionEvent(
                    id = 1,
                    timestamp = 1_000L,
                    fromActivity = com.podometer.domain.model.ActivityState.STILL,
                    toActivity = com.podometer.domain.model.ActivityState.WALKING,
                    isManualOverride = false,
                ),
            ),
        )
        assertFalse(state.todaySteps == 0 && state.transitions.isEmpty())
    }

    // ─── Class existence checks ───────────────────────────────────────────────

    @Test
    fun `EmptyStatesKt exists in com_podometer_ui_dashboard package`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.EmptyStatesKt")
        assertTrue(
            "EmptyStatesKt should exist in com.podometer.ui.dashboard",
            clazz.name.startsWith("com.podometer.ui.dashboard"),
        )
    }
}

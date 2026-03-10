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
    fun `isFirstLaunch condition is true when steps are zero`() {
        val state = DashboardUiState(todaySteps = 0)
        assertTrue(state.todaySteps == 0)
    }

    @Test
    fun `isFirstLaunch condition is false when steps are non-zero`() {
        val state = DashboardUiState(todaySteps = 100)
        assertFalse(state.todaySteps == 0)
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

    // ─── String resource key existence checks ────────────────────────────────

    /**
     * Verifies that the R.string class contains all string keys used by EmptyStates composables.
     * These tests fail if the key is missing from strings.xml and therefore from the generated
     * R.string class.
     */
    private fun assertStringKeyExists(keyName: String) {
        val rString = Class.forName("com.podometer.R\$string")
        val field = try {
            rString.getField(keyName)
        } catch (e: NoSuchFieldException) {
            null
        }
        assertTrue(
            "R.string.$keyName must exist — add it to strings.xml",
            field != null,
        )
    }

    @Test
    fun `R_string_empty_first_launch_message key exists`() {
        assertStringKeyExists("empty_first_launch_message")
    }

    @Test
    fun `R_string_sensor_notice_accelerometer key exists`() {
        assertStringKeyExists("sensor_notice_accelerometer")
    }

    @Test
    fun `R_string_sensor_notice_none key exists`() {
        assertStringKeyExists("sensor_notice_none")
    }

    @Test
    fun `R_string_permissions_required_title key exists`() {
        assertStringKeyExists("permissions_required_title")
    }

    @Test
    fun `R_string_permissions_required_message key exists`() {
        assertStringKeyExists("permissions_required_message")
    }

    @Test
    fun `R_string_permissions_button_open_settings key exists`() {
        assertStringKeyExists("permissions_button_open_settings")
    }

    @Test
    fun `R_string_cd_sensor_notice key exists`() {
        assertStringKeyExists("cd_sensor_notice")
    }

    @Test
    fun `R_string_cd_permissions_recovery key exists`() {
        assertStringKeyExists("cd_permissions_recovery")
    }
}

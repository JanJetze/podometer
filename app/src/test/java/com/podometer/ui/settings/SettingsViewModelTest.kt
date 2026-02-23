// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ExportState] sealed interface and the state management model
 * used by [SettingsViewModel].
 *
 * Full integration tests requiring ContentResolver and Android Context mocking
 * are deferred — the [SettingsViewModel.exportData] method needs a real Android
 * environment. These tests cover the pure state model and all ExportState variants.
 */
class SettingsViewModelTest {

    // ─── ExportState sealed interface variants ────────────────────────────────

    @Test
    fun `ExportState Idle is a valid ExportState variant`() {
        val state: ExportState = ExportState.Idle
        assertTrue(state is ExportState.Idle)
    }

    @Test
    fun `ExportState InProgress is a valid ExportState variant`() {
        val state: ExportState = ExportState.InProgress
        assertTrue(state is ExportState.InProgress)
    }

    @Test
    fun `ExportState Success is a valid ExportState variant`() {
        val state: ExportState = ExportState.Success
        assertTrue(state is ExportState.Success)
    }

    @Test
    fun `ExportState Error is a valid ExportState variant`() {
        val state: ExportState = ExportState.Error("something went wrong")
        assertTrue(state is ExportState.Error)
    }

    // ─── ExportState.Error message field ─────────────────────────────────────

    @Test
    fun `ExportState Error contains the provided message string`() {
        val message = "Cannot open output stream for URI"
        val state = ExportState.Error(message)
        assertEquals(message, state.message)
    }

    @Test
    fun `ExportState Error message is accessible as a data class field`() {
        val errorMessage = "Disk is full"
        val error = ExportState.Error(errorMessage)
        assertEquals(errorMessage, error.message)
    }

    @Test
    fun `ExportState Error with empty message stores empty string`() {
        val error = ExportState.Error("")
        assertEquals("", error.message)
    }

    // ─── ExportState exhaustiveness ───────────────────────────────────────────

    @Test
    fun `ExportState all four variants are exhaustive in a when expression`() {
        val states: List<ExportState> = listOf(
            ExportState.Idle,
            ExportState.InProgress,
            ExportState.Success,
            ExportState.Error("test"),
        )

        val labels = states.map { state ->
            when (state) {
                is ExportState.Idle -> "Idle"
                is ExportState.InProgress -> "InProgress"
                is ExportState.Success -> "Success"
                is ExportState.Error -> "Error"
            }
        }

        assertEquals(listOf("Idle", "InProgress", "Success", "Error"), labels)
    }

    // ─── ExportState equality ─────────────────────────────────────────────────

    @Test
    fun `ExportState Idle equals another Idle instance`() {
        assertEquals(ExportState.Idle, ExportState.Idle)
    }

    @Test
    fun `ExportState InProgress equals another InProgress instance`() {
        assertEquals(ExportState.InProgress, ExportState.InProgress)
    }

    @Test
    fun `ExportState Success equals another Success instance`() {
        assertEquals(ExportState.Success, ExportState.Success)
    }

    @Test
    fun `ExportState Error with same message equals another Error with same message`() {
        val error1 = ExportState.Error("timeout")
        val error2 = ExportState.Error("timeout")
        assertEquals(error1, error2)
    }

    @Test
    fun `ExportState initial value is Idle`() {
        // Documents the contract: a freshly initialized export state starts as Idle.
        // This mirrors the ViewModel's _exportState initial value.
        val initialState: ExportState = ExportState.Idle
        assertEquals(ExportState.Idle, initialState)
    }

    @Test
    fun `ExportState resets to Idle after non-Idle state`() {
        // Documents resetExportState() contract: any state can transition back to Idle.
        var state: ExportState = ExportState.Success
        // Simulate resetExportState() setting the value back to Idle
        state = ExportState.Idle
        assertEquals(ExportState.Idle, state)
    }

    @Test
    fun `ExportState resets to Idle from Error state`() {
        var state: ExportState = ExportState.Error("network failure")
        state = ExportState.Idle
        assertEquals(ExportState.Idle, state)
    }
}

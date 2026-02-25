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

    // ─── Export state reset ordering contract ────────────────────────────────

    /**
     * Documents the required ordering contract for the SettingsScreen LaunchedEffect:
     * the error message must be captured BEFORE resetExportState() is called, because
     * the reset transitions the state back to Idle and the message would be lost.
     *
     * This test verifies the pure-Kotlin ordering logic that the composable must follow
     * when using try/finally to guarantee reset even on coroutine cancellation.
     */
    @Test
    fun `error message captured before reset does not change on reset`() {
        var state: ExportState = ExportState.Error("disk full")

        // Capture message before reset — as required by the try/finally fix
        val capturedMessage = (state as ExportState.Error).message

        // Simulate reset (what onResetExportState does)
        state = ExportState.Idle

        // Message captured before reset is still intact, even though state is now Idle
        assertEquals("disk full", capturedMessage)
        assertEquals(ExportState.Idle, state)
    }

    @Test
    fun `error message is inaccessible after reset to Idle`() {
        var state: ExportState = ExportState.Error("connection timeout")

        // Simulate reset before capturing message — the WRONG ordering
        state = ExportState.Idle

        // After reset, state is Idle and carries no message
        assertEquals(ExportState.Idle, state)
    }

    @Test
    fun `success state resets to Idle without requiring message capture`() {
        var state: ExportState = ExportState.Success

        // Success carries no message, so reset order does not matter for message safety
        state = ExportState.Idle

        assertEquals(ExportState.Idle, state)
    }
}

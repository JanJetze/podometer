// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [StepTrackingService.runBlockingWithDefault].
 *
 * Verifies the error-handling logic: when a suspend block throws, the helper
 * logs the failure and returns the provided default. [CancellationException]
 * is always rethrown so coroutine cancellation signals are never swallowed.
 */
class StepTrackingServiceRunBlockingTest {

    // ─── happy path ──────────────────────────────────────────────────────────

    @Test
    fun `runBlockingWithDefault returns block result when block succeeds`() {
        val result = StepTrackingService.runBlockingWithDefault(
            default = 42,
            tag = "test read",
        ) { 99 }

        assertEquals("Should return block result when no exception is thrown", 99, result)
    }

    @Test
    fun `runBlockingWithDefault returns float block result when block succeeds`() {
        val result = StepTrackingService.runBlockingWithDefault(
            default = 0.00075f,
            tag = "read stride length preference",
        ) { 0.001f }

        assertEquals("Should return block result for Float type", 0.001f, result, 0.00001f)
    }

    @Test
    fun `runBlockingWithDefault returns zero int when block returns zero`() {
        val result = StepTrackingService.runBlockingWithDefault(
            default = -1,
            tag = "test zero",
        ) { 0 }

        assertEquals("Should return 0 from block, not the default", 0, result)
    }

    // ─── exception / fail-safe path ──────────────────────────────────────────

    @Test
    fun `runBlockingWithDefault returns default when block throws RuntimeException`() {
        val result = StepTrackingService.runBlockingWithDefault(
            default = 0,
            tag = "read current-hour steps from DB",
        ) { throw RuntimeException("DB unavailable") }

        assertEquals("Should return default (0) when RuntimeException is thrown", 0, result)
    }

    @Test
    fun `runBlockingWithDefault returns default stride when block throws Exception`() {
        val result = StepTrackingService.runBlockingWithDefault(
            default = StepAccumulator.DEFAULT_STRIDE_LENGTH_KM,
            tag = "read stride length preference",
        ) { throw Exception("DataStore corrupted") }

        assertEquals(
            "Should return DEFAULT_STRIDE_LENGTH_KM when exception is thrown",
            StepAccumulator.DEFAULT_STRIDE_LENGTH_KM,
            result,
            0.00001f,
        )
    }

    @Test
    fun `runBlockingWithDefault returns default when block throws IllegalStateException`() {
        val result = StepTrackingService.runBlockingWithDefault(
            default = 0,
            tag = "read today total steps from DB",
        ) { throw IllegalStateException("DataStore read failed") }

        assertEquals("Should return 0 (default) when IllegalStateException is thrown", 0, result)
    }

    // ─── CancellationException must be rethrown ───────────────────────────────

    @Test(expected = CancellationException::class)
    fun `runBlockingWithDefault rethrows CancellationException`() {
        StepTrackingService.runBlockingWithDefault(
            default = 0,
            tag = "cancellation test",
        ) { throw CancellationException("cancelled") }
    }

    @Test(expected = CancellationException::class)
    fun `runBlockingWithDefault rethrows CancellationException for float default`() {
        StepTrackingService.runBlockingWithDefault(
            default = StepAccumulator.DEFAULT_STRIDE_LENGTH_KM,
            tag = "cancellation test float",
        ) { throw CancellationException("cancelled") }
    }
}

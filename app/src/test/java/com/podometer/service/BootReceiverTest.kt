// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BootReceiver.resolveAutoStartEnabled].
 *
 * Verifies the fail-open error-handling logic: when reading the auto-start
 * preference throws an exception the helper defaults to `true` so that step
 * tracking is not silently lost on boot.
 */
class BootReceiverTest {

    // ─── happy path ──────────────────────────────────────────────────────────

    @Test
    fun `resolveAutoStartEnabled returns true when preference is true`() {
        val result = BootReceiver.resolveAutoStartEnabled { true }

        assertTrue("Should return the preference value when no exception is thrown", result)
    }

    @Test
    fun `resolveAutoStartEnabled returns false when preference is false`() {
        val result = BootReceiver.resolveAutoStartEnabled { false }

        assertFalse("Should return the preference value when no exception is thrown", result)
    }

    // ─── fail-open error handling ─────────────────────────────────────────────

    @Test
    fun `resolveAutoStartEnabled defaults to true when preference throws RuntimeException`() {
        val result = BootReceiver.resolveAutoStartEnabled {
            throw RuntimeException("DataStore unavailable")
        }

        assertTrue(
            "Should default to true (fail-open) when an exception is thrown",
            result,
        )
    }

    @Test
    fun `resolveAutoStartEnabled defaults to true when preference throws IllegalStateException`() {
        val result = BootReceiver.resolveAutoStartEnabled {
            throw IllegalStateException("Coroutine cancelled")
        }

        assertTrue(
            "Should default to true (fail-open) when an IllegalStateException is thrown",
            result,
        )
    }

    @Test
    fun `resolveAutoStartEnabled defaults to true when preference throws Exception`() {
        val result = BootReceiver.resolveAutoStartEnabled {
            @Suppress("TooGenericExceptionThrown")
            throw Exception("Generic failure")
        }

        assertTrue(
            "Should default to true (fail-open) for any Exception subtype",
            result,
        )
    }

    // ─── coroutine cancellation ───────────────────────────────────────────────

    @Test(expected = CancellationException::class)
    fun `resolveAutoStartEnabled rethrows CancellationException`() {
        BootReceiver.resolveAutoStartEnabled {
            throw CancellationException("cancelled")
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.util

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PermissionChecker] pure-Kotlin helper functions.
 *
 * These tests run on the JVM without Android framework dependencies.
 */
class PermissionCheckerTest {

    // ─── areEssentialPermissionsGranted ──────────────────────────────────────

    @Test
    fun `areEssentialPermissionsGranted returns true when ACTIVITY_RECOGNITION granted`() {
        val results = mapOf(
            Manifest.permission.ACTIVITY_RECOGNITION to true,
            Manifest.permission.POST_NOTIFICATIONS to false,
        )
        assertTrue(areEssentialPermissionsGranted(results))
    }

    @Test
    fun `areEssentialPermissionsGranted returns false when ACTIVITY_RECOGNITION denied`() {
        val results = mapOf(
            Manifest.permission.ACTIVITY_RECOGNITION to false,
            Manifest.permission.POST_NOTIFICATIONS to true,
        )
        assertFalse(areEssentialPermissionsGranted(results))
    }

    @Test
    fun `areEssentialPermissionsGranted returns false when result map is empty`() {
        assertFalse(areEssentialPermissionsGranted(emptyMap()))
    }

    @Test
    fun `areEssentialPermissionsGranted returns false when ACTIVITY_RECOGNITION not in map`() {
        val results = mapOf(
            Manifest.permission.POST_NOTIFICATIONS to true,
        )
        assertFalse(areEssentialPermissionsGranted(results))
    }

    // ─── areAllPermissionsGranted ─────────────────────────────────────────────

    @Test
    fun `areAllPermissionsGranted returns true when all permissions granted`() {
        val results = mapOf(
            Manifest.permission.ACTIVITY_RECOGNITION to true,
            Manifest.permission.POST_NOTIFICATIONS to true,
        )
        assertTrue(areAllPermissionsGranted(results))
    }

    @Test
    fun `areAllPermissionsGranted returns false when POST_NOTIFICATIONS denied`() {
        val results = mapOf(
            Manifest.permission.ACTIVITY_RECOGNITION to true,
            Manifest.permission.POST_NOTIFICATIONS to false,
        )
        assertFalse(areAllPermissionsGranted(results))
    }

    @Test
    fun `areAllPermissionsGranted returns false when ACTIVITY_RECOGNITION denied`() {
        val results = mapOf(
            Manifest.permission.ACTIVITY_RECOGNITION to false,
            Manifest.permission.POST_NOTIFICATIONS to true,
        )
        assertFalse(areAllPermissionsGranted(results))
    }

    @Test
    fun `areAllPermissionsGranted returns false when all permissions denied`() {
        val results = mapOf(
            Manifest.permission.ACTIVITY_RECOGNITION to false,
            Manifest.permission.POST_NOTIFICATIONS to false,
        )
        assertFalse(areAllPermissionsGranted(results))
    }

    @Test
    fun `areAllPermissionsGranted returns false when result map is empty`() {
        assertFalse(areAllPermissionsGranted(emptyMap()))
    }

    // ─── isActivityRecognitionDenied ─────────────────────────────────────────

    @Test
    fun `isActivityRecognitionDenied returns true when ACTIVITY_RECOGNITION denied`() {
        val results = mapOf(
            Manifest.permission.ACTIVITY_RECOGNITION to false,
        )
        assertTrue(isActivityRecognitionDenied(results))
    }

    @Test
    fun `isActivityRecognitionDenied returns false when ACTIVITY_RECOGNITION granted`() {
        val results = mapOf(
            Manifest.permission.ACTIVITY_RECOGNITION to true,
        )
        assertFalse(isActivityRecognitionDenied(results))
    }

    @Test
    fun `isActivityRecognitionDenied returns false when ACTIVITY_RECOGNITION absent from map`() {
        assertFalse(isActivityRecognitionDenied(mapOf(Manifest.permission.POST_NOTIFICATIONS to true)))
    }
}

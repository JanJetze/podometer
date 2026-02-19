// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import com.podometer.domain.model.ActivityState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [NotificationHelper] formatting logic.
 *
 * All tested functions are pure companion functions that do not require an
 * Android Context, making them suitable for plain JUnit execution.
 */
class NotificationHelperTest {

    // ─── formatSteps ─────────────────────────────────────────────────────────

    @Test
    fun `formatSteps formats zero as 0`() {
        val result = NotificationHelper.formatSteps(0, Locale.US)
        assertEquals("0 steps", result)
    }

    @Test
    fun `formatSteps formats small number without grouping separator`() {
        val result = NotificationHelper.formatSteps(999, Locale.US)
        assertEquals("999 steps", result)
    }

    @Test
    fun `formatSteps formats thousands with grouping separator`() {
        val result = NotificationHelper.formatSteps(7432, Locale.US)
        assertEquals("7,432 steps", result)
    }

    @Test
    fun `formatSteps formats ten thousands with grouping separator`() {
        val result = NotificationHelper.formatSteps(12345, Locale.US)
        assertEquals("12,345 steps", result)
    }

    @Test
    fun `formatSteps formats one step as singular`() {
        val result = NotificationHelper.formatSteps(1, Locale.US)
        assertEquals("1 step", result)
    }

    // ─── formatDistance ──────────────────────────────────────────────────────

    @Test
    fun `formatDistance formats zero to one decimal`() {
        val result = NotificationHelper.formatDistance(0.0f)
        assertEquals("0.0 km", result)
    }

    @Test
    fun `formatDistance formats value to one decimal place`() {
        val result = NotificationHelper.formatDistance(5.2f)
        assertEquals("5.2 km", result)
    }

    @Test
    fun `formatDistance rounds to one decimal place`() {
        val result = NotificationHelper.formatDistance(3.456f)
        assertEquals("3.5 km", result)
    }

    @Test
    fun `formatDistance formats double-digit km`() {
        val result = NotificationHelper.formatDistance(12.7f)
        assertEquals("12.7 km", result)
    }

    // ─── activityDisplayText ─────────────────────────────────────────────────

    @Test
    fun `activityDisplayText returns Walking for WALKING`() {
        val result = NotificationHelper.activityDisplayText(ActivityState.WALKING)
        assertEquals("Walking", result)
    }

    @Test
    fun `activityDisplayText returns Cycling for CYCLING`() {
        val result = NotificationHelper.activityDisplayText(ActivityState.CYCLING)
        assertEquals("Cycling", result)
    }

    @Test
    fun `activityDisplayText returns Still for STILL`() {
        val result = NotificationHelper.activityDisplayText(ActivityState.STILL)
        assertEquals("Still", result)
    }

    // ─── buildContentText ─────────────────────────────────────────────────────

    @Test
    fun `buildContentText MINIMAL returns only step count`() {
        val result = NotificationHelper.buildContentText(
            steps = 7432,
            distanceKm = 5.2f,
            activity = ActivityState.WALKING,
            style = NotificationStyle.MINIMAL,
            locale = Locale.US,
        )
        assertEquals("7,432 steps", result)
    }

    @Test
    fun `buildContentText MINIMAL singular step`() {
        val result = NotificationHelper.buildContentText(
            steps = 1,
            distanceKm = 0.001f,
            activity = ActivityState.WALKING,
            style = NotificationStyle.MINIMAL,
            locale = Locale.US,
        )
        assertEquals("1 step", result)
    }

    @Test
    fun `buildContentText DETAILED returns steps dot distance dot activity`() {
        val result = NotificationHelper.buildContentText(
            steps = 7432,
            distanceKm = 5.2f,
            activity = ActivityState.WALKING,
            style = NotificationStyle.DETAILED,
            locale = Locale.US,
        )
        assertEquals("7,432 steps · 5.2 km · Walking", result)
    }

    @Test
    fun `buildContentText DETAILED with CYCLING activity`() {
        val result = NotificationHelper.buildContentText(
            steps = 0,
            distanceKm = 0.0f,
            activity = ActivityState.CYCLING,
            style = NotificationStyle.DETAILED,
            locale = Locale.US,
        )
        assertEquals("0 steps · 0.0 km · Cycling", result)
    }

    @Test
    fun `buildContentText DETAILED with STILL activity`() {
        val result = NotificationHelper.buildContentText(
            steps = 1200,
            distanceKm = 0.9f,
            activity = ActivityState.STILL,
            style = NotificationStyle.DETAILED,
            locale = Locale.US,
        )
        assertEquals("1,200 steps · 0.9 km · Still", result)
    }

    // ─── NotificationStyle enum ───────────────────────────────────────────────

    @Test
    fun `NotificationStyle enum has MINIMAL value`() {
        val style = NotificationStyle.MINIMAL
        assertEquals("MINIMAL", style.name)
    }

    @Test
    fun `NotificationStyle enum has DETAILED value`() {
        val style = NotificationStyle.DETAILED
        assertEquals("DETAILED", style.name)
    }

    @Test
    fun `NotificationStyle class exists in service package`() {
        val clazz = NotificationStyle::class.java
        assertEquals("com.podometer.service", clazz.packageName)
    }

    @Test
    fun `NotificationHelper class exists in service package`() {
        val clazz = NotificationHelper::class.java
        assertEquals("com.podometer.service", clazz.packageName)
    }
}

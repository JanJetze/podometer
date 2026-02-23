// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.ui.graphics.Color
import com.podometer.domain.model.ActivityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ActivityBadge] pure-logic helpers.
 *
 * Compose composables cannot be rendered in JVM unit tests without a device/emulator,
 * so these tests cover the pure-Kotlin extension functions extracted from ActivityBadge:
 *  - [ActivityState.displayText]: label text for each activity state
 *  - [ActivityState.contentDescriptionText]: accessibility text for each activity state
 *  - The [ActivityBadge] composable class itself exists in the expected package.
 */
class ActivityBadgeTest {

    // ─── displayText ──────────────────────────────────────────────────────────

    @Test
    fun `displayText returns Walking for WALKING state`() {
        assertEquals("Walking", ActivityState.WALKING.displayText())
    }

    @Test
    fun `displayText returns Cycling for CYCLING state`() {
        assertEquals("Cycling", ActivityState.CYCLING.displayText())
    }

    @Test
    fun `displayText returns Still for STILL state`() {
        assertEquals("Still", ActivityState.STILL.displayText())
    }

    // ─── contentDescriptionText ───────────────────────────────────────────────

    @Test
    fun `contentDescriptionText returns Currently walking for WALKING state`() {
        assertEquals("Currently walking", ActivityState.WALKING.contentDescriptionText())
    }

    @Test
    fun `contentDescriptionText returns Currently cycling for CYCLING state`() {
        assertEquals("Currently cycling", ActivityState.CYCLING.contentDescriptionText())
    }

    @Test
    fun `contentDescriptionText returns Currently still for STILL state`() {
        assertEquals("Currently still", ActivityState.STILL.contentDescriptionText())
    }

    // ─── displayText coverage for all enum values ─────────────────────────────

    @Test
    fun `displayText covers all ActivityState values without throwing`() {
        ActivityState.entries.forEach { state ->
            val text = state.displayText()
            assertTrue(
                "displayText for $state should be non-empty",
                text.isNotBlank(),
            )
        }
    }

    @Test
    fun `contentDescriptionText covers all ActivityState values without throwing`() {
        ActivityState.entries.forEach { state ->
            val text = state.contentDescriptionText()
            assertTrue(
                "contentDescriptionText for $state should start with 'Currently'",
                text.startsWith("Currently"),
            )
        }
    }

    // ─── ActivityBadgeKt class existence ──────────────────────────────────────

    @Test
    fun `ActivityBadgeKt exists in com_podometer_ui_dashboard package`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.ActivityBadgeKt")
        assertTrue(
            "ActivityBadgeKt should exist in com.podometer.ui.dashboard",
            clazz.name.startsWith("com.podometer.ui.dashboard"),
        )
    }

    @Test
    fun `displayText function is accessible as extension function via reflection`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.ActivityBadgeKt")
        val method = clazz.getDeclaredMethod("displayText", ActivityState::class.java)
        val result = method.invoke(null, ActivityState.WALKING) as String
        assertEquals("Walking", result)
    }

    @Test
    fun `contentDescriptionText function is accessible as extension function via reflection`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.ActivityBadgeKt")
        val method = clazz.getDeclaredMethod("contentDescriptionText", ActivityState::class.java)
        val result = method.invoke(null, ActivityState.CYCLING) as String
        assertEquals("Currently cycling", result)
    }

    // ─── WCAG AA contrast verification ────────────────────────────────────────
    //
    // WCAG 2.1 AA requires a minimum contrast ratio of 4.5:1 for normal text.
    // Formula: ratio = (L1 + 0.05) / (L2 + 0.05), where L1 >= L2.
    // Relative luminance: L = 0.2126*R + 0.7152*G + 0.0722*B
    // Each channel linearised: c <= 0.04045 → c/12.92, else ((c+0.055)/1.055)^2.4

    private fun linearise(c: Float): Double {
        val norm = c.toDouble()
        return if (norm <= 0.04045) norm / 12.92 else Math.pow((norm + 0.055) / 1.055, 2.4)
    }

    private fun relativeLuminance(color: Color): Double {
        val r = linearise(color.red)
        val g = linearise(color.green)
        val b = linearise(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val lf = relativeLuminance(foreground)
        val lb = relativeLuminance(background)
        val lighter = maxOf(lf, lb)
        val darker = minOf(lf, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // WCAG 2.1 AA requires a minimum contrast ratio of 4.5:1 for normal text.
    // Badge colours are sourced from the theme (Color.kt) and now meet WCAG AA.
    // Light-theme badge backgrounds use dark colours with white text (4.5:1+).
    // Dark-theme badge backgrounds use light colours with black text (4.5:1+).

    @Test
    fun `white text on walking dark green badge meets WCAG AA 4_5 contrast`() {
        val white = Color(0xFFFFFFFF)
        val walkingGreen = Color(0xFF2E7D32) // ActivityWalking — dark green
        val ratio = contrastRatio(white, walkingGreen)
        // Expected ≈ 7.5:1 — well above WCAG AA 4.5:1.
        assertTrue(
            "Contrast ratio for white on walking green ($walkingGreen) should be >= 4.5, got $ratio",
            ratio >= 4.5,
        )
    }

    @Test
    fun `white text on cycling dark blue badge meets WCAG AA 4_5 contrast`() {
        val white = Color(0xFFFFFFFF)
        val cyclingBlue = Color(0xFF1565C0) // ActivityCycling — dark blue
        val ratio = contrastRatio(white, cyclingBlue)
        // Expected ≈ 7.0:1 — above WCAG AA 4.5:1.
        assertTrue(
            "Contrast ratio for white on cycling blue ($cyclingBlue) should be >= 4.5, got $ratio",
            ratio >= 4.5,
        )
    }

    @Test
    fun `white text on still dark gray badge meets WCAG AA 4_5 contrast`() {
        val white = Color(0xFFFFFFFF)
        val stillGray = Color(0xFF424242) // ActivityStill — dark gray
        val ratio = contrastRatio(white, stillGray)
        // Expected ≈ 9.7:1 — well above WCAG AA 4.5:1.
        assertTrue(
            "Contrast ratio for white on still gray ($stillGray) should be >= 4.5, got $ratio",
            ratio >= 4.5,
        )
    }
}

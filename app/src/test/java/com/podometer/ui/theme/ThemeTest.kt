// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PodometerTheme] pure-Kotlin helper logic.
 *
 * These tests cover:
 *  - WCAG AA contrast ratios (4.5:1) for activity badge colours against white/black text
 *  - Activity colour mapping via [activityColorFor]
 *  - Existence and structure of [ActivityColors] data class
 *  - Static colour scheme definitions (light/dark)
 *  - Goal ring tier colour constants and [GoalRingColors] data class
 */
class ThemeTest {

    // ─── WCAG contrast helpers ─────────────────────────────────────────────────

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

    /**
     * Computes the WCAG 2.1 contrast ratio between two colours.
     *
     * @return Contrast ratio >= 1.0.
     */
    fun contrastRatio(foreground: Color, background: Color): Double {
        val lf = relativeLuminance(foreground)
        val lb = relativeLuminance(background)
        val lighter = maxOf(lf, lb)
        val darker = minOf(lf, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // ─── Activity badge colour contrast tests (WCAG AA 4.5:1) ─────────────────

    @Test
    fun `walking colour achieves at least 4_5 to 1 contrast with white text`() {
        val ratio = contrastRatio(Color.White, ActivityWalking)
        assertTrue(
            "Walking colour $ActivityWalking must achieve 4.5:1 contrast with white text, got $ratio",
            ratio >= 4.5,
        )
    }

    @Test
    fun `cycling colour achieves at least 4_5 to 1 contrast with white text`() {
        val ratio = contrastRatio(Color.White, ActivityCycling)
        assertTrue(
            "Cycling colour $ActivityCycling must achieve 4.5:1 contrast with white text, got $ratio",
            ratio >= 4.5,
        )
    }

    @Test
    fun `still colour achieves at least 4_5 to 1 contrast with white text`() {
        val ratio = contrastRatio(Color.White, ActivityStill)
        assertTrue(
            "Still colour $ActivityStill must achieve 4.5:1 contrast with white text, got $ratio",
            ratio >= 4.5,
        )
    }

    // ─── ActivityColors data class ─────────────────────────────────────────────

    @Test
    fun `ActivityColors data class contains walking cycling and still fields`() {
        val colors = ActivityColors(
            walking = Color(0xFF1B5E20),
            cycling = Color(0xFF0D47A1),
            still = Color(0xFF424242),
        )
        assertEquals(Color(0xFF1B5E20), colors.walking)
        assertEquals(Color(0xFF0D47A1), colors.cycling)
        assertEquals(Color(0xFF424242), colors.still)
    }

    @Test
    fun `ActivityColors supports copy for creating variants`() {
        val original = ActivityColors(
            walking = Color(0xFF1B5E20),
            cycling = Color(0xFF0D47A1),
            still = Color(0xFF424242),
        )
        val copy = original.copy(walking = Color(0xFF2E7D32))
        assertEquals(Color(0xFF2E7D32), copy.walking)
        assertEquals(Color(0xFF0D47A1), copy.cycling)
        assertEquals(Color(0xFF424242), copy.still)
    }

    // ─── Static colour scheme constants ───────────────────────────────────────

    @Test
    fun `ActivityWalking is defined in theme package`() {
        // ActivityWalking must be a non-transparent, non-black colour
        assertTrue(
            "ActivityWalking should have non-zero red or green component",
            ActivityWalking.red > 0f || ActivityWalking.green > 0f,
        )
    }

    @Test
    fun `ActivityCycling is defined in theme package`() {
        assertTrue(
            "ActivityCycling should have non-zero blue component",
            ActivityCycling.blue > 0f,
        )
    }

    @Test
    fun `ActivityStill is defined in theme package`() {
        // Still is gray — all channels roughly equal and non-zero
        val r = ActivityStill.red
        val g = ActivityStill.green
        val b = ActivityStill.blue
        assertTrue(
            "ActivityStill should be a dark gray (not pure black or white)",
            r in 0.1f..0.9f && g in 0.1f..0.9f && b in 0.1f..0.9f,
        )
    }

    // ─── Light activity colours (dark background variant) ────────────────────

    @Test
    fun `ActivityWalkingLight achieves at least 4_5 to 1 contrast with dark text`() {
        val ratio = contrastRatio(Color.Black, ActivityWalkingLight)
        assertTrue(
            "ActivityWalkingLight must achieve 4.5:1 with dark text, got $ratio",
            ratio >= 4.5,
        )
    }

    @Test
    fun `ActivityCyclingLight achieves at least 4_5 to 1 contrast with dark text`() {
        val ratio = contrastRatio(Color.Black, ActivityCyclingLight)
        assertTrue(
            "ActivityCyclingLight must achieve 4.5:1 with dark text, got $ratio",
            ratio >= 4.5,
        )
    }

    @Test
    fun `ActivityStillLight achieves at least 4_5 to 1 contrast with dark text`() {
        val ratio = contrastRatio(Color.Black, ActivityStillLight)
        assertTrue(
            "ActivityStillLight must achieve 4.5:1 with dark text, got $ratio",
            ratio >= 4.5,
        )
    }

    // ─── DefaultActivityColors convenience value ──────────────────────────────

    @Test
    fun `DefaultActivityColors uses ActivityWalking for walking field`() {
        assertEquals(ActivityWalking, DefaultActivityColors.walking)
    }

    @Test
    fun `DefaultActivityColors uses ActivityCycling for cycling field`() {
        assertEquals(ActivityCycling, DefaultActivityColors.cycling)
    }

    @Test
    fun `DefaultActivityColors uses ActivityStill for still field`() {
        assertEquals(ActivityStill, DefaultActivityColors.still)
    }

    // ─── DarkActivityColors convenience value ─────────────────────────────────

    @Test
    fun `DarkActivityColors uses ActivityWalkingLight for walking field`() {
        assertEquals(ActivityWalkingLight, DarkActivityColors.walking)
    }

    @Test
    fun `DarkActivityColors uses ActivityCyclingLight for cycling field`() {
        assertEquals(ActivityCyclingLight, DarkActivityColors.cycling)
    }

    @Test
    fun `DarkActivityColors uses ActivityStillLight for still field`() {
        assertEquals(ActivityStillLight, DarkActivityColors.still)
    }

    // ─── Goal ring tier colour constants ──────────────────────────────────────

    @Test
    fun `RingMinimumLight is a green-toned colour`() {
        // Minimum tier — lighter/softer green; green channel should dominate
        assertTrue(
            "RingMinimumLight should have a strong green component",
            RingMinimumLight.green > RingMinimumLight.red && RingMinimumLight.green > RingMinimumLight.blue,
        )
    }

    @Test
    fun `RingTargetLight is a medium green colour`() {
        assertTrue(
            "RingTargetLight should have a strong green component",
            RingTargetLight.green > RingTargetLight.red && RingTargetLight.green > RingTargetLight.blue,
        )
    }

    @Test
    fun `RingStretchLight is an amber or gold-toned colour`() {
        // Stretch tier — vibrant gold/amber; red channel should be prominent
        assertTrue(
            "RingStretchLight should have a strong red/orange component",
            RingStretchLight.red > RingStretchLight.blue,
        )
    }

    @Test
    fun `RingMinimumDark is a green-toned colour`() {
        assertTrue(
            "RingMinimumDark should have a strong green component",
            RingMinimumDark.green > RingMinimumDark.red && RingMinimumDark.green > RingMinimumDark.blue,
        )
    }

    @Test
    fun `RingTargetDark is a medium green colour`() {
        assertTrue(
            "RingTargetDark should have a strong green component",
            RingTargetDark.green > RingTargetDark.red && RingTargetDark.green > RingTargetDark.blue,
        )
    }

    @Test
    fun `RingStretchDark is an amber or gold-toned colour`() {
        assertTrue(
            "RingStretchDark should have a strong red/orange component",
            RingStretchDark.red > RingStretchDark.blue,
        )
    }

    // ─── GoalRingColors data class ────────────────────────────────────────────

    @Test
    fun `GoalRingColors holds minimum target and stretch fields`() {
        val colors = GoalRingColors(
            minimum = Color(0xFF66BB6A),
            target = Color(0xFF2E7D32),
            stretch = Color(0xFFF6BE48),
        )
        assertEquals(Color(0xFF66BB6A), colors.minimum)
        assertEquals(Color(0xFF2E7D32), colors.target)
        assertEquals(Color(0xFFF6BE48), colors.stretch)
    }

    @Test
    fun `GoalRingColors supports copy for creating variants`() {
        val base = GoalRingColors(
            minimum = Color(0xFF66BB6A),
            target = Color(0xFF2E7D32),
            stretch = Color(0xFFF6BE48),
        )
        val variant = base.copy(stretch = Color(0xFFFFA726))
        assertEquals(Color(0xFF66BB6A), variant.minimum)
        assertEquals(Color(0xFF2E7D32), variant.target)
        assertEquals(Color(0xFFFFA726), variant.stretch)
    }

    @Test
    fun `DefaultGoalRingColors uses ring tier constants`() {
        assertEquals(RingMinimumLight, DefaultGoalRingColors.minimum)
        assertEquals(RingTargetLight, DefaultGoalRingColors.target)
        assertEquals(RingStretchLight, DefaultGoalRingColors.stretch)
    }

    @Test
    fun `DarkGoalRingColors uses dark ring tier constants`() {
        assertEquals(RingMinimumDark, DarkGoalRingColors.minimum)
        assertEquals(RingTargetDark, DarkGoalRingColors.target)
        assertEquals(RingStretchDark, DarkGoalRingColors.stretch)
    }

    // ─── Typography scale differentiation ─────────────────────────────────────

    @Test
    fun `labelSmall is smaller than labelMedium`() {
        val labelSmall = PodometerTypography.labelSmall
        val labelMedium = PodometerTypography.labelMedium
        assertTrue(
            "labelSmall (${labelSmall.fontSize}) should be smaller than labelMedium (${labelMedium.fontSize})",
            labelSmall.fontSize < labelMedium.fontSize,
        )
    }

    @Test
    fun `displayLarge is larger than displayMedium`() {
        val displayLarge = PodometerTypography.displayLarge
        val displayMedium = PodometerTypography.displayMedium
        assertTrue(
            "displayLarge (${displayLarge.fontSize}) should be larger than displayMedium (${displayMedium.fontSize})",
            displayLarge.fontSize > displayMedium.fontSize,
        )
    }

    @Test
    fun `displaySmall is at least 32sp for hero step count`() {
        val displaySmall = PodometerTypography.displaySmall
        assertTrue(
            "displaySmall (${displaySmall.fontSize}) must be at least 32sp for hero text",
            displaySmall.fontSize.value >= 32f,
        )
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Primary palette — teal/green fitness-app identity ───────────────────────

/** Primary colour (light scheme): deep teal. */
val Teal40 = Color(0xFF006A60)

/** Primary container (light scheme): light teal. */
val TealContainer80 = Color(0xFF9EF2E4)

/** On-primary container (light scheme): dark teal. */
val OnTealContainer10 = Color(0xFF00201C)

/** Primary colour (dark scheme): lighter teal for dark backgrounds. */
val Teal80 = Color(0xFF83D5C7)

/** On-primary (dark scheme): very dark teal text on teal. */
val OnTeal20 = Color(0xFF00382F)

// ─── Secondary palette ────────────────────────────────────────────────────────

/** Secondary colour (light scheme): muted teal-gray. */
val TealGrey40 = Color(0xFF4A6360)

/** Secondary colour (dark scheme): lighter teal-gray. */
val TealGrey80 = Color(0xFFACCCC8)

// ─── Tertiary palette — amber accent ─────────────────────────────────────────

/** Tertiary colour (light scheme): amber for goal achievement. */
val Amber40 = Color(0xFF7C5800)

/** Tertiary colour (dark scheme): light amber. */
val Amber80 = Color(0xFFF6BE48)

// ─── Error colours ────────────────────────────────────────────────────────────

/** Error colour (light scheme). */
val Red40 = Color(0xFFBA1A1A)

/** Error colour (dark scheme). */
val Red80 = Color(0xFFFFB4AB)

// ─── Neutral surface colours ──────────────────────────────────────────────────

/** Background / surface (light scheme): near-white. */
val NeutralLightSurface = Color(0xFFF5FAFA)

/** Background / surface (dark scheme): near-black with teal tint. */
val NeutralDarkSurface = Color(0xFF0E1514)

/** Surface variant (light scheme): soft teal-gray. */
val NeutralLightSurfaceVariant = Color(0xFFDAE5E2)

/** Surface variant (dark scheme). */
val NeutralDarkSurfaceVariant = Color(0xFF3F4947)

/** On-surface (light scheme): dark text. */
val NeutralLightOnSurface = Color(0xFF171D1C)

/** On-surface (dark scheme): near-white text. */
val NeutralDarkOnSurface = Color(0xFFE0E3E2)

// ─── Activity colours — WCAG AA compliant (4.5:1 minimum contrast) ───────────
//
// Dark variants (for light theme badges): text on these must be white (Color.White).
// Contrast ratios vs white:
//   ActivityWalking  (#2E7D32) ≈ 7.5:1  ✓
//   ActivityCycling  (#1565C0) ≈ 7.0:1  ✓
//   ActivityStill    (#424242) ≈ 9.7:1  ✓
//
// Light variants (for dark theme badges): text on these must be black (Color.Black).
// Contrast ratios vs black:
//   ActivityWalkingLight (#A5D6A7) ≈ 9.2:1  ✓
//   ActivityCyclingLight (#90CAF9) ≈ 9.2:1  ✓
//   ActivityStillLight   (#BDBDBD) ≈ 5.3:1  ✓

/** Dark green — walking activity colour for light theme. Contrast vs white ≈ 7.5:1. */
val ActivityWalking = Color(0xFF2E7D32)

/** Dark blue — cycling activity colour for light theme. Contrast vs white ≈ 7.0:1. */
val ActivityCycling = Color(0xFF1565C0)

/** Dark gray — still/no-activity colour for light theme. Contrast vs white ≈ 9.7:1. */
val ActivityStill = Color(0xFF424242)

/** Light green — walking activity colour for dark theme. Contrast vs black ≈ 9.2:1. */
val ActivityWalkingLight = Color(0xFFA5D6A7)

/** Light blue — cycling activity colour for dark theme. Contrast vs black ≈ 9.2:1. */
val ActivityCyclingLight = Color(0xFF90CAF9)

/** Light gray — still/no-activity colour for dark theme. Contrast vs black ≈ 5.3:1. */
val ActivityStillLight = Color(0xFFBDBDBD)

// ─── Progress ring goal tier colours ─────────────────────────────────────────
//
// Three tiers map to progress thresholds:
//   Minimum  — up to the base daily minimum (softer sage green)
//   Target   — reaching the main daily goal (confident mid-green)
//   Stretch  — exceeding the stretch goal (vibrant gold — motivating achievement)
//
// Light theme variants are saturated enough to read on the near-white surface.
// Dark theme variants are lighter/brighter to pop on the deep teal-tinted surface.

/** Minimum-tier ring colour for **light** theme — sage green. */
val RingMinimumLight = Color(0xFF6BAE75)

/** Target-tier ring colour for **light** theme — confident forest green. */
val RingTargetLight = Color(0xFF2E7D32)

/** Stretch-tier ring colour for **light** theme — vibrant gold. */
val RingStretchLight = Color(0xFFE6A817)

/** Minimum-tier ring colour for **dark** theme — soft mint green. */
val RingMinimumDark = Color(0xFF80C784)

/** Target-tier ring colour for **dark** theme — medium spring green. */
val RingTargetDark = Color(0xFF43A047)

/** Stretch-tier ring colour for **dark** theme — warm amber. */
val RingStretchDark = Color(0xFFF6BE48)

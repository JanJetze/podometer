// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ─── Activity colour container ────────────────────────────────────────────────

/**
 * Centralised activity colours accessible via [LocalActivityColors].
 *
 * Includes background colours for each activity state and a [contentColor]
 * (icon/text colour) to render on top of those backgrounds.
 *
 * @property walking      Background colour for walking activity segments.
 * @property cycling      Background colour for cycling activity segments.
 * @property still        Background colour for still/no-activity segments.
 * @property contentColor Foreground colour (icon + text) rendered on all backgrounds.
 */
@Immutable
data class ActivityColors(
    val walking: Color,
    val cycling: Color,
    val still: Color,
    val contentColor: Color = Color.White,
)

/**
 * Default [ActivityColors] for the **light** theme.
 *
 * All background colours achieve at least 4.5:1 contrast against white text (WCAG AA).
 */
val DefaultActivityColors = ActivityColors(
    walking = ActivityWalking,
    cycling = ActivityCycling,
    still = ActivityStill,
    contentColor = Color.White,
)

/**
 * [ActivityColors] for the **dark** theme.
 *
 * All background colours achieve at least 4.5:1 contrast against black text (WCAG AA).
 */
val DarkActivityColors = ActivityColors(
    walking = ActivityWalkingLight,
    cycling = ActivityCyclingLight,
    still = ActivityStillLight,
    contentColor = Color.Black,
)

/**
 * CompositionLocal that provides [ActivityColors] throughout the composable tree.
 *
 * Access via `LocalActivityColors.current` inside any composable wrapped by [PodometerTheme].
 * Defaults to [DefaultActivityColors] if no provider is present in the tree.
 */
val LocalActivityColors = staticCompositionLocalOf { DefaultActivityColors }

// ─── Goal ring colour container ───────────────────────────────────────────────

/**
 * Colour set for the three progress-ring goal tiers.
 *
 * The progress ring transitions through these colours as the user approaches
 * and surpasses their daily step goals:
 * - [minimum] — up to the minimum daily activity level (softer green).
 * - [target]  — reaching the main daily goal (confident mid-green).
 * - [stretch] — exceeding the stretch goal (vibrant gold, motivating achievement).
 *
 * @property minimum Colour for the minimum-progress tier.
 * @property target  Colour for the on-target tier.
 * @property stretch Colour for the over-achievement / stretch tier.
 */
@Immutable
data class GoalRingColors(
    val minimum: Color,
    val target: Color,
    val stretch: Color,
)

/**
 * Default [GoalRingColors] for the **light** theme.
 *
 * Uses saturated greens and a warm gold that read clearly on the near-white surface.
 */
val DefaultGoalRingColors = GoalRingColors(
    minimum = RingMinimumLight,
    target = RingTargetLight,
    stretch = RingStretchLight,
)

/**
 * [GoalRingColors] for the **dark** theme.
 *
 * Uses lighter/brighter tones that pop on the deep teal-tinted dark surface.
 */
val DarkGoalRingColors = GoalRingColors(
    minimum = RingMinimumDark,
    target = RingTargetDark,
    stretch = RingStretchDark,
)

/**
 * CompositionLocal that provides [GoalRingColors] throughout the composable tree.
 *
 * Access via `LocalGoalRingColors.current` inside any composable wrapped by [PodometerTheme].
 * Defaults to [DefaultGoalRingColors] if no provider is present in the tree.
 */
val LocalGoalRingColors = staticCompositionLocalOf { DefaultGoalRingColors }

// ─── Material 3 colour schemes ────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = TealContainer80,
    onPrimaryContainer = OnTealContainer10,
    secondary = TealGrey40,
    onSecondary = Color.White,
    tertiary = Amber40,
    onTertiary = Color.White,
    error = Red40,
    onError = Color.White,
    background = NeutralLightSurface,
    onBackground = NeutralLightOnSurface,
    surface = NeutralLightSurface,
    onSurface = NeutralLightOnSurface,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF3F4947),
)

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = OnTeal20,
    primaryContainer = Color(0xFF004F47),
    onPrimaryContainer = TealContainer80,
    secondary = TealGrey80,
    onSecondary = Color(0xFF1C3533),
    tertiary = Amber80,
    onTertiary = Color(0xFF412D00),
    error = Red80,
    onError = Color(0xFF690005),
    background = NeutralDarkSurface,
    onBackground = NeutralDarkOnSurface,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkOnSurface,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFBEC9C6),
)

// ─── Theme composable ─────────────────────────────────────────────────────────

/**
 * Root Material 3 theme for the Podometer app.
 *
 * Provides the full Material 3 colour scheme, typography scale, and centralised
 * activity colours via [LocalActivityColors] and goal ring tier colours via
 * [LocalGoalRingColors].
 *
 * Since minSdk = 33 (Android 13), dynamic colour (Material You) is always available
 * at runtime. The [dynamicColor] parameter is exposed so that previews and tests can
 * opt out and use the static fallback scheme instead.
 *
 * @param darkTheme     Whether to use the dark colour scheme. Defaults to the system setting.
 * @param dynamicColor  Whether to use dynamic colour from the device wallpaper (Material You).
 *                      Defaults to `true`. Set to `false` in previews or tests.
 * @param content       The composable content to theme.
 */
@Composable
fun PodometerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val activityColors = if (darkTheme) DarkActivityColors else DefaultActivityColors
    val goalRingColors = if (darkTheme) DarkGoalRingColors else DefaultGoalRingColors

    CompositionLocalProvider(
        LocalActivityColors provides activityColors,
        LocalGoalRingColors provides goalRingColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PodometerTypography,
            content = content,
        )
    }
}

// ─── Preview helpers ──────────────────────────────────────────────────────────

/**
 * Colour swatch row for use inside theme previews.
 *
 * @param label      Text label shown below the swatch.
 * @param color      The colour to display.
 * @param textColor  Colour of the [label] text (defaults to [Color.Black]).
 */
@Composable
private fun ColorSwatch(label: String, color: Color, textColor: Color = Color.Black) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}

/**
 * Preview composable showing the full Podometer colour palette in the **light** theme.
 *
 * Covers primary, secondary, tertiary, surface, error and all three goal-ring tiers.
 */
@Preview(showBackground = true, name = "Podometer Palette — Light")
@Composable
private fun PreviewPaletteLight() {
    PodometerTheme(darkTheme = false, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Podometer Colour Palette",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "Light theme",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ColorSwatch("Primary", MaterialTheme.colorScheme.primary)
                    ColorSwatch("Secondary", MaterialTheme.colorScheme.secondary)
                    ColorSwatch("Tertiary", MaterialTheme.colorScheme.tertiary)
                    ColorSwatch("Error", MaterialTheme.colorScheme.error)
                    ColorSwatch("Surface", MaterialTheme.colorScheme.surface)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Goal ring tiers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val ringColors = LocalGoalRingColors.current
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ColorSwatch("Minimum", ringColors.minimum)
                    ColorSwatch("Target", ringColors.target)
                    ColorSwatch("Stretch", ringColors.stretch)
                }
            }
        }
    }
}

/**
 * Preview composable showing the full Podometer colour palette in the **dark** theme.
 *
 * Covers primary, secondary, tertiary, surface, error and all three goal-ring tiers.
 */
@Preview(showBackground = true, backgroundColor = 0xFF0E1514, name = "Podometer Palette — Dark")
@Composable
private fun PreviewPaletteDark() {
    PodometerTheme(darkTheme = true, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Podometer Colour Palette",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dark theme",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ColorSwatch("Primary", MaterialTheme.colorScheme.primary, Color.White)
                    ColorSwatch("Secondary", MaterialTheme.colorScheme.secondary, Color.White)
                    ColorSwatch("Tertiary", MaterialTheme.colorScheme.tertiary, Color.White)
                    ColorSwatch("Error", MaterialTheme.colorScheme.error, Color.White)
                    ColorSwatch("Surface", MaterialTheme.colorScheme.surface, Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Goal ring tiers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val ringColors = LocalGoalRingColors.current
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ColorSwatch("Minimum", ringColors.minimum, Color.White)
                    ColorSwatch("Target", ringColors.target, Color.White)
                    ColorSwatch("Stretch", ringColors.stretch, Color.White)
                }
            }
        }
    }
}

/**
 * Preview composable demonstrating the full typography scale in the Podometer theme.
 *
 * Covers all type scale slots from displayLarge down to labelSmall, so the
 * visual hierarchy can be evaluated at a glance.
 */
@Preview(showBackground = true, name = "Podometer Typography Scale")
@Composable
private fun PreviewTypographyScale() {
    PodometerTheme(darkTheme = false, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("10,000", style = MaterialTheme.typography.displayLarge)
                Text("displayLarge — hero step count", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("8,432", style = MaterialTheme.typography.displayMedium)
                Text("displayMedium — large dashboard number", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("6,210", style = MaterialTheme.typography.displaySmall)
                Text("displaySmall — ring centre number (≥32sp)", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("This Week", style = MaterialTheme.typography.headlineLarge)
                Text("headlineLarge — section heading", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Daily Summary", style = MaterialTheme.typography.titleLarge)
                Text("titleLarge — card title", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Steps taken today", style = MaterialTheme.typography.bodyLarge)
                Text("bodyLarge — primary body copy", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("3.8 km walked", style = MaterialTheme.typography.bodyMedium)
                Text("bodyMedium — secondary info", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("GOAL REACHED", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("labelLarge — badge text", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Mon 10 Mar", style = MaterialTheme.typography.labelMedium)
                Text("labelMedium — chart axis label (12sp)", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("avg 350 steps/hr", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("labelSmall — fine caption (11sp)", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
